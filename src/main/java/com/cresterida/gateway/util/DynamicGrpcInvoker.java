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

            // Validate request fields against proto definition first
            for (String fieldName : requestBody.fieldNames()) {
                Descriptors.FieldDescriptor field = inputDescriptor.findFieldByName(fieldName);
                if (field == null) {
                    String errorMsg = String.format(
                        "Invalid field '%s' in request. Available fields are: %s",
                        fieldName,
                        inputDescriptor.getFields().stream()
                            .map(Descriptors.FieldDescriptor::getName)
                            .toList()
                    );
                    LOGGER.error(errorMsg);
                    return Future.failedFuture(errorMsg);
                }
            }

            DynamicMessage.Builder requestBuilder = DynamicMessage.newBuilder(inputDescriptor);
            try {
                // Use JsonFormat to parse the validated JSON body into the Protobuf message
                JsonFormat.parser()
                        .ignoringUnknownFields() // Now we want to fail on unknown fields
                        .merge(requestBody.encode(), requestBuilder);
            } catch (InvalidProtocolBufferException e) {
                String errorMsg = String.format(
                    "Invalid request format: %s. Expected format matches proto definition: %s",
                    e.getMessage(),
                    inputDescriptor.toProto()
                );
                LOGGER.error(errorMsg);
                return Future.failedFuture(errorMsg);
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
                    handleGrpcError(e, promise);
                    return null;
                } finally {
                    // Ensure channel is shutdown
                    shutdownChannel(channel);
                }
                return null;
            });

        } catch (Exception e) {
            handleGrpcError(e, promise);
        }

        return promise.future();
    }

    private ManagedChannel createChannel(ServiceInstance instance) {
        return ManagedChannelBuilder.forAddress(instance.getHost(), instance.getPort())
            .usePlaintext() // For development. Use TLS in production
            .keepAliveTimeout(10, TimeUnit.SECONDS)
            .keepAliveWithoutCalls(true)
            .idleTimeout(defaultTimeout, TimeUnit.SECONDS)
            .maxInboundMessageSize(10 * 1024 * 1024) // 10MB
            .maxRetryAttempts(1)
            .enableRetry()
            .build();
    }

    private void handleGrpcError(Throwable error, Promise<JsonObject> promise) {
        String errorMessage;
        if (error instanceof io.grpc.StatusRuntimeException) {
            io.grpc.StatusRuntimeException statusError = (io.grpc.StatusRuntimeException) error;
            switch (statusError.getStatus().getCode()) {
                case UNAVAILABLE:
                    errorMessage = "Service is currently unavailable. Please try again later.";
                    break;
                case DEADLINE_EXCEEDED:
                    errorMessage = "Request timed out. Please try again.";
                    break;
                case UNIMPLEMENTED:
                    errorMessage = "The requested operation is not implemented.";
                    break;
                case INVALID_ARGUMENT:
                    errorMessage = "Invalid request: " + statusError.getStatus().getDescription();
                    break;
                default:
                    errorMessage = "gRPC error: " + statusError.getStatus().getCode() + " - " + statusError.getStatus().getDescription();
            }
        } else if (error instanceof java.util.concurrent.TimeoutException
                  || error instanceof io.netty.handler.timeout.ReadTimeoutException) {
            errorMessage = "Request timed out while waiting for response.";
        } else {
            errorMessage = "Internal error: " + error.getMessage();
        }
        LOGGER.error("gRPC call failed: {}", errorMessage, error);
        promise.fail(errorMessage);
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
