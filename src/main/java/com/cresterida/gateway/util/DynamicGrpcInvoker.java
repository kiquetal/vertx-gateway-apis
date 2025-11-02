package com.cresterida.gateway.util;

import com.cresterida.gateway.model.ServiceDefinition;
import com.cresterida.gateway.model.ServiceInstance;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.MethodDescriptor;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class DynamicGrpcInvoker {
    private static final Logger LOGGER = LoggerFactory.getLogger(DynamicGrpcInvoker.class);
    private final Vertx vertx;

    private final int defaultTimeout;

    public DynamicGrpcInvoker(Vertx vertx, int defaultTimeoutSeconds) {
        this.vertx = vertx;
        this.defaultTimeout = defaultTimeoutSeconds;
    }

    public Future<JsonObject> invoke(ServiceDefinition service, String methodName, JsonObject requestBody) {
        Promise<JsonObject> promise = Promise.promise();

        try {
            // Get active service instance
            ServiceInstance instance = service.getActiveInstance();
            if (instance == null) {
                return Future.failedFuture("No active service instance available");
            }

            // Create and configure channel
            ManagedChannel channel = createChannel(instance);

            // Create method descriptor and get descriptors
            String fullServiceName = service.getPackageName() + "." + service.getName();
            DescriptorHolder descriptorHolder = createMethodDescriptor(
                service.getProtoDefinition(),
                fullServiceName,
                methodName
            );

            MethodDescriptor<Message, Message> methodDescriptor = descriptorHolder.methodDescriptor;
            Descriptors.FileDescriptor fileDescriptor = descriptorHolder.fileDescriptor;

            // Get input descriptor from method
            Descriptors.ServiceDescriptor serviceDescriptor = fileDescriptor.findServiceByName(
                fullServiceName.substring(fullServiceName.lastIndexOf('.') + 1));
            if (serviceDescriptor == null) {
                return Future.failedFuture("Service not found: " + fullServiceName);
            }

            Descriptors.MethodDescriptor methodDesc = serviceDescriptor.findMethodByName(methodName);
            if (methodDesc == null) {
                return Future.failedFuture("Method not found: " + methodName);
            }

            Descriptors.Descriptor inputDescriptor = methodDesc.getInputType();

            DynamicMessage.Builder requestBuilder = DynamicMessage.newBuilder(inputDescriptor);
            try {
                // Use JsonFormat to parse the *entire* JSON body into the Protobuf message
                // This handles all type conversions, nested objects, and field names.
                JsonFormat.parser()
                        .ignoringUnknownFields()
                        .merge(requestBody.encode(), requestBuilder);
            } catch (InvalidProtocolBufferException e) {
                LOGGER.error("Invalid JSON body for request: {}", e.getMessage());
                return Future.failedFuture("Invalid JSON body for request: " + e.getMessage());
            }

            DynamicMessage request = requestBuilder.build();

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
                    try {
                        // Use the printer to convert the protobuf message to a valid JSON string
                        String jsonResponseString = JsonFormat.printer()
                                .preservingProtoFieldNames()
                                .print(response);

                        // Parse that valid JSON string into a Vert.x JsonObject
                        JsonObject jsonResponse = new JsonObject(jsonResponseString);

                        // Complete the promise with the JsonObject
                        promise.complete(jsonResponse);
                    } catch (InvalidProtocolBufferException e) {
                        promise.fail(e);
                    }

                } catch (Exception e) {
                    LOGGER.error("Error while processing request", e);
                    return Future.<JsonObject>failedFuture(e);
                } finally {
                    // Ensure channel is shutdown
                    shutdownChannel(channel);
                }
                return null;
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

    private static class DescriptorHolder {
        final MethodDescriptor<Message, Message> methodDescriptor;
        final Descriptors.FileDescriptor fileDescriptor;

        DescriptorHolder(MethodDescriptor<Message, Message> methodDescriptor, Descriptors.FileDescriptor fileDescriptor) {
            this.methodDescriptor = methodDescriptor;
            this.fileDescriptor = fileDescriptor;
        }
    }

    private DescriptorHolder createMethodDescriptor(
            String protoDefinition,
            String serviceName,
            String methodName) throws Exception {

        // Use the shared builder to get the descriptors
        ProtoDescriptorBuilder.BuildResult buildResult = ProtoDescriptorBuilder.buildFromProtoDefinition(
            serviceName.replaceAll("[^a-zA-Z0-9]", "_"), // Create a safe file prefix from service name
            protoDefinition
        );

        Descriptors.FileDescriptor fileDescriptor = buildResult.getFileDescriptor();

        // Get the service descriptor
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
        MethodDescriptor<Message, Message> methodDescriptor = MethodDescriptor.<Message, Message>newBuilder()
            .setType(MethodDescriptor.MethodType.UNARY)
            .setFullMethodName(
                MethodDescriptor.generateFullMethodName(serviceName, methodName)
            )
            .setRequestMarshaller(new DynamicMessageMarshaller(methodDesc.getInputType()))
            .setResponseMarshaller(new DynamicMessageMarshaller(methodDesc.getOutputType()))
            .build();

        return new DescriptorHolder(methodDescriptor, fileDescriptor);
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



}
