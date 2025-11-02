package com.cresterida.gateway.handlers;

import com.cresterida.gateway.model.ServiceDefinition;
import com.cresterida.gateway.model.EndpointDefinition;
import com.cresterida.gateway.util.DynamicGrpcInvoker;
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

import java.util.Map;

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

            // Create service descriptor and input message
            DescriptorProtos.FileDescriptorSet.Builder fileDescriptorSet = DescriptorProtos.FileDescriptorSet.newBuilder();

            // Start with a basic file descriptor proto
            DescriptorProtos.FileDescriptorProto.Builder fileBuilder = DescriptorProtos.FileDescriptorProto.newBuilder();

            // Get the raw proto definition
            String rawProto = sd.getProtoDefinition();

            // Try to parse the proto definition text
            try {
                TextFormat.merge(rawProto, fileBuilder);

                // Make sure the package name is set
                if (!fileBuilder.hasPackage()) {
                    fileBuilder.setPackage(sd.getPackageName());
                }

                // Add the file to the descriptor set
                fileDescriptorSet.addFile(fileBuilder.build());
            } catch (TextFormat.ParseException e) {
                LOGGER.error("Failed to parse proto definition", e);
                throw new RuntimeException("Failed to parse proto definition: " + e.getMessage(), e);
            }

            fileDescriptorSet.addFile(fileBuilder.build());

            // Build the file descriptor
            Descriptors.FileDescriptor fileDescriptor = Descriptors.FileDescriptor.buildFrom(
                fileDescriptorSet.getFile(0),
                new Descriptors.FileDescriptor[0]
            );

            // Get input message type descriptor
            Descriptors.Descriptor inputDescriptor = fileDescriptor.findMessageTypeByName(endpoint.getInputMessage());
            if (inputDescriptor == null) {
                handleError(ctx, 500, "Input message type not found: " + endpoint.getInputMessage());
                return;
            }

            // Build request message from JSON
            JsonObject requestBody = ctx.body().asJsonObject();
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
