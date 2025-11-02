Dynamic gRPC-to-JSON ProxyThis project is a dynamic gRPC API gateway built with Vert.x. It acts as a bridge, converting standard HTTP/JSON requests into backend gRPC calls on the fly, without requiring any pre-compiled Java stubs.The gateway is designed to be configured entirely by JSON definitions, allowing you to add, remove, or modify gRPC services without recompiling or restarting the proxy.Core ConceptsThe proxy works in three main stages:Service Definition: You define a service, its gRPC methods, and its .proto schema in a JSON file.Dynamic Proto Compilation: At runtime, the proxy reads this JSON, extracts the protoDefinition string, and uses a protoc compiler to create a binary "descriptor" of the service. This descriptor is a complete "map" of the service, its methods, and its message types.Dynamic Invocation: When an HTTP request arrives:The gateway uses JsonFormat.parser() to parse the JSON body into a generic DynamicMessage based on the compiled descriptor.The DynamicGrpcInvoker makes the gRPC call with this DynamicMessage.The DynamicGrpcInvoker receives a DynamicMessage response, which it converts back into a JSON string using JsonFormat.printer().Example UsageThe entire system is driven by a service definition.1. Define the ServiceYou define your gRPC service in a JSON file. This example defines a Greeter service with two methods, including one that uses lists (repeated fields).{
"id": "greeter-service",
"name": "Greeter",
"packageName": "helloworld",
"protoDefinition": "syntax = \"proto3\";\n\npackage helloworld;\n\nservice Greeter {\n  rpc SayHello (HelloRequest) returns (HelloReply) {}\n  rpc GreetMany (GreetManyRequest) returns (GreetManyReply) {}\n}\n\nmessage HelloRequest {\n  string name = 1;\n}\nmessage HelloReply {\n  string message = 1;\n}\n\nmessage GreetManyRequest {\n  repeated string names = 1;\n}\n\nmessage GreetManyReply {\n  repeated string messages = 1;\n}",
"pathPrefix": "/api/greeter",
"endpoints": [
{
"name": "sayHello",
"methodName": "SayHello",
"inputMessage": "HelloRequest",
"outputMessage": "HelloReply"
},
{
"name": "greetMany",
"methodName": "GreetMany",
"inputMessage": "GreetManyRequest",
"outputMessage": "GreetManyReply"
}
]
}
2. Make an HTTP RequestBased on the definition above, the gateway automatically exposes the greetMany endpoint. The JsonFormat library handles the JSON array-to-repeated field mapping for you.Request:POST http://localhost:8080/api/greeter/greetManyRequest Body (JSON):{
   "names": ["Alice", "Bob", "Charlie"]
   }
3. Receive a JSON ResponseThe DynamicGrpcInvoker receives the binary gRPC response, converts it back to JSON, and sends it to the client.Response Body (JSON):{
   "messages": ["Hello Alice", "Hello Bob", "Hello Charlie"]
   }
   A Note on protoc-jar Obsolescence (Upgrade Path)The current code in DynamicGrpcProxyHandler.java uses the com.github.os72:protoc-jar library. This is a convenient library that bundles the protoc compiler inside a Java JAR file.The Problem: protoc-jar is ObsoleteThe protoc-jar library is no longer maintained. Its last release was in 2020 and it bundles protoc version 3.11.4.The Protobuf language has evolved since then. If you try to use new Protobuf features (like optional field keywords in proto3) in your protoDefinition, the old 3.11.4 compiler will not understand the syntax and the request will fail.The Solution: Use a System-Installed protocThe long-term, production-safe solution is to use a modern protoc compiler installed directly in the environment and modify the Java code to call it.This solution works universally for both local development (host) and production (container).Step 1: Modify the Java CodeFirst, you must change DynamicGrpcProxyHandler.java to call the system's protoc command instead of Protoc.runProtoc().Find this block (the "Before"):// ...
   import com.github.os72.protocjar.Protoc;
   // ...

// 4. Run the compiler using protoc-jar
LOGGER.debug("Running protoc compiler...");
int exitCode = Protoc.runProtoc(protocArgs);
if (exitCode != 0) {
LOGGER.error("protoc compiler failed with exit code: {}", exitCode);
handleError(ctx, 500, "protoc compiler failed. Exit code: " + exitCode);
return;
}
// ...
Replace it with this code (the "After"):// ...
// Make sure to import these at the top of your file:
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
// ...

// 4. Run the system compiler using ProcessBuilder
LOGGER.debug("Running system protoc compiler...");
try {
// Create a list for the command and its arguments
List<String> command = new ArrayList<>();
// The command is 'protoc'. On Windows, this might be 'protoc.exe'
command.add("protoc");
// Add all the arguments
command.addAll(Arrays.asList(protocArgs));

    // Start the process
    ProcessBuilder pb = new ProcessBuilder(command);
    
    // Redirect error stream to standard output for logging
    pb.redirectErrorStream(true);
    Process process = pb.start();

    // Log the output from the compiler for debugging
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
        String line;
        while ((line = reader.readLine()) != null) {
            LOGGER.debug("protoc: {}", line);
        }
    }

    // Wait for the process to complete and check the exit code
    int exitCode = process.waitFor();
    if (exitCode != 0) {
        LOGGER.error("System protoc compiler failed with exit code: {}", exitCode);
        handleError(ctx, 500, "System protoc compiler failed. Exit code: " + exitCode);
        return;
    }
} catch (IOException | InterruptedException e) {
if (e instanceof InterruptedException) {
Thread.currentThread().interrupt();
}
LOGGER.error("Failed to execute system 'protoc'", e);
handleError(ctx, 500, "Failed to execute system 'protoc': " + e.getMessage());
return;
}
// ...
Finally, you can remove import com.github.os72.protocjar.Protoc; from the top of the file and delete the protoc-jar dependency from your pom.xml.Step 2: Configure the EnvironmentThis Java code now requires the protoc command to be available in the system's PATH.For Production (Dockerfile):You must install protoc in your container image.# Start from a base Java 21 image
FROM eclipse-temurin:21-jdk-jammy

# --- INSTALL PROTOC ---
# Option A: Install from Ubuntu's repository (easiest, but may be old)
RUN apt-get update && \
apt-get install -y protobuf-compiler && \
apt-get clean && \
rm -rf /var/lib/apt/lists/*

# Option B: Install a specific NEW version from GitHub (Recommended)
# RUN apt-get update && apt-get install -y curl unzip && \
#     PROTOC_VERSION="3.21.9" && \
#     PROTOC_ZIP="protoc-${PROTOC_VERSION}-linux-x86_64.zip" && \
#     curl -OL "[https://github.com/protocolbuffers/protobuf/releases/download/v$](https://github.com/protocolbuffers/protobuf/releases/download/v$){PROTOC_VERSION}/${PROTOC_ZIP}" && \
#     unzip -o ${PROTOC_ZIP} -d /usr/local bin/protoc && \
#     unzip -o ${PROTOC_ZIP} -d /usr/local 'include/*' && \
#     rm -f ${PROTOC_ZIP} && \
#     apt-get remove -y curl unzip && apt-get autoremove -y && \
#     apt-get clean && rm -rf /var/lib/apt/lists/*
# ---

# Copy your fat JAR into the container
WORKDIR /app
COPY target/vertx-gateway-apis-*-fat.jar ./app.jar

# Expose the port your Vert.x app listens on
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
For Development (Local Host):You must install protoc on your local machine and add it to your PATH.macOS (using Homebrew):brew install protobuf
Ubuntu/Debian:sudo apt-get update
sudo apt-get install protobuf-compiler
Windows / Manual Install:Go to the Protobuf GitHub Releases page.Download the protoc-*-<your-os>.zip file for the latest version.Unzip it.Add the bin directory from the unzipped folder to your system's PATH environment variable.
