package com.cresterida.gateway.handlers;

import com.cresterida.gateway.model.ServiceDefinition;
import com.cresterida.gateway.model.EndpointDefinition;
import com.cresterida.gateway.util.DynamicGrpcInvoker;
import com.github.os72.protocjar.Protoc;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import com.google.protobuf.TextFormat;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import io.vertx.core.Vertx;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class DynamicGrpcProxyHandler implements Handler<RoutingContext> {
    private static final Logger LOGGER = LogManager.getLogger(DynamicGrpcProxyHandler.class);
    private final DynamicGrpcInvoker grpcInvoker;

    public DynamicGrpcProxyHandler(Vertx vertx) {
        this.grpcInvoker = new DynamicGrpcInvoker(vertx, 30); // 30 seconds timeout
    }

    private void handleError(RoutingContext ctx, int statusCode, String message) {
        ctx.response()
            .setStatusCode(statusCode)
            .putHeader("Content-Type", "application/json")
            .end(new JsonObject()
                .put("error", message)
                .put("status", statusCode)
                .put("path", ctx.request().path())
                .encode());
    }

    @Override
    public void handle(RoutingContext ctx) {
        ServiceDefinition sd = ctx.get("service");
        if (sd == null) {
            ctx.next();
            return;
        }

        // Paths for temp files
        Path tempProtoFile = null;
        Path tempDescFile = null;
        try {
            // Extract method name from path
            String path = ctx.request().path();
            String methodName = path.substring(path.lastIndexOf('/') + 1);

            // Find matching endpoint
            Map<String, EndpointDefinition> endpoints = sd.getEndpoints();
            EndpointDefinition endpoint = endpoints.get(methodName);
            if (endpoint == null) {
                handleError(ctx, 404, "Endpoint not found: " + methodName);
                return;
            }

            String rawProto = sd.getProtoDefinition();
            String serviceName = sd.getName(); // e.g., "Greeter"
            String fullServiceName = serviceName;
            if (sd.getPackageName() != null && !sd.getPackageName().isEmpty()) {
                fullServiceName = sd.getPackageName() + "." + serviceName; // e.g., "helloworld.Greeter"
            }

            // 2. Create temporary files
            tempProtoFile = Files.createTempFile(sd.getId(), ".proto");
            tempDescFile = Files.createTempFile(sd.getId(), ".desc");
            Files.writeString(tempProtoFile, rawProto, StandardCharsets.UTF_8);

            // 3. Prepare 'protoc' compiler arguments
            String[] protocArgs = {
                    // Include path for the temp file
                    "-I=" + tempProtoFile.getParent().toAbsolutePath().toString(),
                    // Include standard Google types (like Timestamp)
                    "--include_imports",
                    // Output path for the binary descriptor set
                    "--descriptor_set_out=" + tempDescFile.toAbsolutePath().toString(),
                    // The proto file to compile
                    tempProtoFile.toAbsolutePath().toString()
            };

            // 4. Run the compiler using protoc-jar
            LOGGER.debug("Running protoc compiler...");
            int exitCode = Protoc.runProtoc(protocArgs);
            if (exitCode != 0) {
                LOGGER.error("protoc compiler failed with exit code: {}", exitCode);
                handleError(ctx, 500, "protoc compiler failed. Exit code: " + exitCode);
                return;
            }

            // 5. Read the generated .desc file
            byte[] descBytes = Files.readAllBytes(tempDescFile);
            if (descBytes.length == 0) {
                LOGGER.error("protoc produced an empty descriptor file.");
                handleError(ctx, 500, "protoc produced an empty descriptor file.");
                return;
            }

            // 6. Parse the .desc bytes into a FileDescriptorSet
            DescriptorProtos.FileDescriptorSet fds = DescriptorProtos.FileDescriptorSet.parseFrom(descBytes);
            if (fds.getFileCount() == 0) {
                LOGGER.error("No file descriptors found in compiled proto set.");
                handleError(ctx, 500, "No file descriptors found in compiled proto set.");
                return;
            }

            // 7. Build FileDescriptors, respecting dependencies (DAG sort)
            Map<String, Descriptors.FileDescriptor> builtDescriptors = new HashMap<>();
            Queue<DescriptorProtos.FileDescriptorProto> toBuild = new LinkedList<>(fds.getFileList());
            int maxAttempts = toBuild.size() * toBuild.size() + 100;
            int attempts = 0;

            while (!toBuild.isEmpty()) {
                if (attempts++ > maxAttempts) {
                    LOGGER.error("Failed to build file descriptors (circular dependency or missing import?)");
                    handleError(ctx, 500, "Failed to build file descriptors (circular dependency?)");
                    return;
                }

                DescriptorProtos.FileDescriptorProto protoFile = toBuild.poll();
                List<Descriptors.FileDescriptor> depDescriptors = new ArrayList<>();
                boolean allDepsReady = true;

                for (String depName : protoFile.getDependencyList()) {
                    Descriptors.FileDescriptor dep = builtDescriptors.get(depName);
                    if (dep != null) {
                        depDescriptors.add(dep);
                    } else {
                        // This dependency isn't built yet
                        allDepsReady = false;
                        break;
                    }
                }

                if (allDepsReady) {
                    try {
                        Descriptors.FileDescriptor fd = Descriptors.FileDescriptor.buildFrom(
                                protoFile,
                                depDescriptors.toArray(new Descriptors.FileDescriptor[0])
                        );
                        builtDescriptors.put(protoFile.getName(), fd);
                    } catch (Descriptors.DescriptorValidationException e) {
                        LOGGER.error("Proto descriptor validation failed for {}", protoFile.getName(), e);
                        handleError(ctx, 500, "Descriptor validation failed: " + e.getMessage());
                        return;
                    }
                } else {
                    // Put it back at the end of the queue
                    toBuild.add(protoFile);
                }
            }

            // 8. Find the main FileDescriptor that contains our service
            final String finalFullServiceName = fullServiceName;
            Descriptors.FileDescriptor mainFileDescriptor = builtDescriptors.values().stream()
                    .filter(fd -> fd.getServices().stream()
                            .anyMatch(s -> s.getFullName().equals(finalFullServiceName)))
                    .findFirst()
                    .orElse(null);

            if (mainFileDescriptor == null) {
                LOGGER.error("Could not find service '{}' in compiled proto definitions.", fullServiceName);
                handleError(ctx, 500, "Could not find service " + fullServiceName + " in compiled proto definitions.");
                return;
            }

            // 9. Find the ServiceDescriptor and MethodDescriptor
            Descriptors.ServiceDescriptor serviceDescriptor = mainFileDescriptor.getServices().stream()
                    .filter(s -> s.getFullName().equals(finalFullServiceName))
                    .findFirst()
                    .orElse(null);

            if (serviceDescriptor == null) {
                // This check is slightly redundant but safe
                handleError(ctx, 500, "Failed to find service " + fullServiceName);
                return;
            }

            Descriptors.MethodDescriptor methodDescriptor = serviceDescriptor.findMethodByName(endpoint.getMethodName());
            if (methodDescriptor == null) {
                handleError(ctx, 500, "Method not found in service: " + endpoint.getMethodName());
                return;
            }

            // 10. Get input/output types
            Descriptors.Descriptor inputDescriptor = methodDescriptor.getInputType();


            JsonObject requestBody = ctx.body().asJsonObject();
            if (requestBody == null) {
                requestBody = new JsonObject(); // Treat empty body as empty JSON
            }

            DynamicMessage.Builder requestBuilder = DynamicMessage.newBuilder(inputDescriptor);
            // Apply input field mappings
            for (Map.Entry<String, String> mapping : endpoint.getInputMapping().entrySet()) {
                String fieldName = mapping.getKey();
                String jsonPath = mapping.getValue();

                // For now, simple direct mapping assuming jsonPath is just the field name
                Descriptors.FieldDescriptor field = inputDescriptor.findFieldByName(fieldName);
                if (field != null && requestBody.containsKey(fieldName)) {
                    Object value = requestBody.getValue(fieldName);
                    requestBuilder.setField(field, value);
                }
            }

            // Make the gRPC call using DynamicGrpcInvoker
            grpcInvoker.invoke(sd, endpoint.getMethodName(), requestBuilder.build())
                .onSuccess(response -> {
                    ctx.response()
                        .putHeader("Content-Type", "application/json")
                        .end(response.encode());
                })
                .onFailure(e -> {
                    LOGGER.error("Error processing gRPC request", e);
                    handleError(ctx, 500, "Error processing gRPC request: " + e.getMessage());
                });

        } catch (Exception e) {
            LOGGER.error("Error setting up gRPC request", e);
            handleError(ctx, 500, "Error setting up gRPC request: " + e.getMessage());
        }
    }
}
