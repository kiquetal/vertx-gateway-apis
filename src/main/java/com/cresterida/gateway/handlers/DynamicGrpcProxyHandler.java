package com.cresterida.gateway.handlers;

import com.cresterida.gateway.model.ServiceDefinition;
import com.cresterida.gateway.model.EndpointDefinition;
import com.cresterida.gateway.model.ServiceInstance;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import com.google.protobuf.TextFormat;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;

public class DynamicGrpcProxyHandler implements Handler<RoutingContext> {
    private static final Logger LOGGER = LogManager.getLogger(DynamicGrpcProxyHandler.class);

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

        // Get the first instance (we'll implement load balancing later)
        List<ServiceInstance> instances = sd.getInstances();
        if (instances.isEmpty()) {
            handleError(ctx, 503, "No service instances available");
            return;
        }
        ServiceInstance instance = instances.get(0);
        String host = instance.getHost();
        int port = instance.getPort();

        // Create channel
        ManagedChannel channel = ManagedChannelBuilder
            .forAddress(host, port)
            .usePlaintext()
            .build();

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

            // Create proto parser and parse the definition
            DescriptorProtos.FileDescriptorSet.Builder fileDescriptorSet = DescriptorProtos.FileDescriptorSet.newBuilder();

            try {
                DescriptorProtos.FileDescriptorProto.Builder fileBuilder = DescriptorProtos.FileDescriptorProto.newBuilder()
                    .setName("service.proto")
                    .setSyntax("proto3")
                    .setPackage(sd.getPackageName());

                // Parse the proto definition text
                TextFormat.merge(sd.getProtoDefinition(), fileBuilder);
                fileDescriptorSet.addFile(fileBuilder.build());

                // Build the file descriptor
                Descriptors.FileDescriptor fileDescriptor = Descriptors.FileDescriptor.buildFrom(
                    fileDescriptorSet.getFile(0),
                    new Descriptors.FileDescriptor[0]
                );

                // Get service descriptor using the service name and package from ServiceDefinition
                String fullServiceName = sd.getPackageName() + "." + sd.getName();
                Descriptors.ServiceDescriptor serviceDescriptor = null;
                for (Descriptors.ServiceDescriptor svc : fileDescriptor.getServices()) {
                    if (svc.getFullName().equals(fullServiceName)) {
                        serviceDescriptor = svc;
                        break;
                    }
                }
                if (serviceDescriptor == null) {
                    handleError(ctx, 500, "Service '" + fullServiceName + "' not found in proto definition");
                    return;
                }

                // Get method descriptor from endpoint
                Descriptors.MethodDescriptor methodDescriptor = serviceDescriptor.findMethodByName(endpoint.getMethodName());
                if (methodDescriptor == null) {
                    handleError(ctx, 500, "Method '" + endpoint.getMethodName() + "' not found in service definition");
                    return;
                }

                // Get message descriptors
                Descriptors.Descriptor inputDescriptor = methodDescriptor.getInputType();
                Descriptors.Descriptor outputDescriptor = methodDescriptor.getOutputType();

                // Build request message
                JsonObject requestBody = ctx.body().asJsonObject();
                DynamicMessage.Builder requestBuilder = DynamicMessage.newBuilder(inputDescriptor);

                // Set the name field directly from the request body
                Descriptors.FieldDescriptor nameField = inputDescriptor.findFieldByName("name");
                if (nameField != null) {
                    requestBuilder.setField(nameField, requestBody.getString("name"));
                }

                // Create method descriptor for gRPC call using the service's full name
                io.grpc.MethodDescriptor<DynamicMessage, DynamicMessage> grpcMethod =
                    io.grpc.MethodDescriptor.<DynamicMessage, DynamicMessage>newBuilder()
                        .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                        .setFullMethodName(io.grpc.MethodDescriptor.generateFullMethodName(
                            fullServiceName, endpoint.getMethodName()))
                        .setRequestMarshaller(new DynamicMessageMarshaller(inputDescriptor))
                        .setResponseMarshaller(new DynamicMessageMarshaller(outputDescriptor))
                        .build();

                // Make the gRPC call
                DynamicMessage request = requestBuilder.build();
                DynamicMessage response = io.grpc.stub.ClientCalls.blockingUnaryCall(
                    channel.newCall(grpcMethod, io.grpc.CallOptions.DEFAULT),
                    request
                );

                // Extract response message field
                JsonObject jsonResponse = new JsonObject();
                Descriptors.FieldDescriptor messageField = outputDescriptor.findFieldByName("message");
                if (messageField != null) {
                    Object value = response.getField(messageField);
                    jsonResponse.put("message", value != null ? value.toString() : "");
                }

                ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .end(jsonResponse.encode());

            } catch (Exception e) {
                LOGGER.error("Error processing gRPC request", e);
                handleError(ctx, 500, "Error processing gRPC request: " + e.getMessage());
            } finally {
                channel.shutdown();
            }
    }
}
