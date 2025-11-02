package com.cresterida.gateway.util;

import com.cresterida.gateway.model.ServiceDefinition;
import com.cresterida.gateway.model.ServiceInstance;
import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.Message;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.MethodDescriptor;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

public class DynamicGrpcInvoker {
    private static final Logger LOGGER = LoggerFactory.getLogger(DynamicGrpcInvoker.class);
    private final Vertx vertx;

    private final int defaultTimeout;

    public DynamicGrpcInvoker(Vertx vertx, int defaultTimeoutSeconds) {
        this.vertx = vertx;
        this.defaultTimeout = defaultTimeoutSeconds;
    }

    public Future<JsonObject> invoke(ServiceDefinition service, String methodName, DynamicMessage request) {
        Promise<JsonObject> promise = Promise.promise();

        try {
            // Get active service instance
            ServiceInstance instance = service.getActiveInstance();
            if (instance == null) {
                return Future.failedFuture("No active service instance available");
            }

            // Create and configure channel
            ManagedChannel channel = createChannel(instance);

            // Create method descriptor
            MethodDescriptor<Message, Message> methodDescriptor = createMethodDescriptor(
                service.getProtoDefinition(),
                service.getPackage() + "." + service.getName(),
                methodName
            );

            // Make the gRPC call
            vertx.executeBlocking(() -> {
                try {
                    Message response = io.grpc.stub.ClientCalls.blockingUnaryCall(
                        channel,
                        methodDescriptor,
                        io.grpc.CallOptions.DEFAULT.withDeadlineAfter(defaultTimeout, TimeUnit.SECONDS),
                        request
                    );

                    // Convert response to JsonObject
                    JsonObject jsonResponse = protoMessageToJson(response);
                    return jsonResponse;
                } catch (Exception e) {
                    LOGGER.error("Error while processing request", e);
                    return Future.<JsonObject>failedFuture(e);
                } finally {
                    // Ensure channel is shutdown
                    shutdownChannel(channel);
                }
            });

        } catch (Exception e) {
            promise.fail(e);
        }

        return promise.future();
    }

    private ManagedChannel createChannel(ServiceInstance instance) {
        return ManagedChannelBuilder.forAddress(instance.getHost(), instance.getPort())
            .usePlaintext() // For development. Use TLS in production
            .build();
    }

    private MethodDescriptor<Message, Message> createMethodDescriptor(
            String protoDefinition,
            String serviceName,
            String methodName) throws Exception {

        // Create temporary proto file
        Path tempProtoFile = Files.createTempFile("temp", ".proto");
        Files.writeString(tempProtoFile, protoDefinition);

        // Generate descriptor set
        Path descriptorFile = Files.createTempFile("descriptor", ".desc");
        String[] protoc_args = new String[]{
            "--proto_path=" + tempProtoFile.getParent(),
            "--descriptor_set_out=" + descriptorFile.toString(),
            tempProtoFile.toString()
        };

        try {
            int exitCode = com.github.os72.protocjar.Protoc.runProtoc(protoc_args);
            if (exitCode != 0) {
                throw new RuntimeException("protoc failed with exit code: " + exitCode);
            }

            // Read and parse the descriptor set
            byte[] descriptorBytes = Files.readAllBytes(descriptorFile);
            DescriptorProtos.FileDescriptorSet descriptorSet =
                DescriptorProtos.FileDescriptorSet.parseFrom(descriptorBytes);

            // Get the service descriptor
            Descriptors.FileDescriptor fileDescriptor =
                Descriptors.FileDescriptor.buildFrom(
                    descriptorSet.getFile(0),
                    new Descriptors.FileDescriptor[]{}
                );

            Descriptors.ServiceDescriptor serviceDescriptor =
                fileDescriptor.findServiceByName(serviceName.substring(serviceName.lastIndexOf('.') + 1));

            if (serviceDescriptor == null) {
                throw new RuntimeException("Service not found: " + serviceName);
            }

            Descriptors.MethodDescriptor methodDesc = serviceDescriptor.findMethodByName(methodName);
            if (methodDesc == null) {
                throw new RuntimeException("Method not found: " + methodName);
            }

            // Create the gRPC method descriptor
            return MethodDescriptor.<Message, Message>newBuilder()
                .setType(MethodDescriptor.MethodType.UNARY)
                .setFullMethodName(
                    MethodDescriptor.generateFullMethodName(serviceName, methodName)
                )
                .setRequestMarshaller(new DynamicMessageMarshaller(methodDesc.getInputType()))
                .setResponseMarshaller(new DynamicMessageMarshaller(methodDesc.getOutputType()))
                .build();

        } finally {
            // Cleanup temporary files
            Files.deleteIfExists(tempProtoFile);
            Files.deleteIfExists(descriptorFile);
        }
    }

    private void shutdownChannel(ManagedChannel channel) {
        try {
            channel.shutdown();
            if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
                channel.shutdownNow();
            }
        } catch (InterruptedException e) {
            channel.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private JsonObject protoMessageToJson(Message message) {
        // Convert proto message to JsonObject
        // This is a simplified version. You might want to use a more sophisticated
        // conversion based on your needs
        return new JsonObject(message.toString());
    }


}
