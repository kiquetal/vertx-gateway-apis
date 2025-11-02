package com.cresterida.gateway.handlers;

import com.cresterida.gateway.model.EndpointDefinition;
import com.cresterida.gateway.model.ServiceDefinition;
import com.cresterida.gateway.util.DynamicGrpcInvoker;
import com.cresterida.gateway.util.ProtoDescriptorBuilder;
import com.google.protobuf.Descriptors;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;

public class DynamicGrpcProxyHandler implements Handler<RoutingContext> {
    private static final Logger LOGGER = LogManager.getLogger(DynamicGrpcProxyHandler.class);
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String APPLICATION_JSON = "application/json";
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    private static final int HTTP_NOT_FOUND = 404;
    private static final int HTTP_SERVER_ERROR = 500;

    private final DynamicGrpcInvoker grpcInvoker;

    public DynamicGrpcProxyHandler(Vertx vertx) {
        this.grpcInvoker = new DynamicGrpcInvoker(vertx, DEFAULT_TIMEOUT_SECONDS);
    }

    private void handleError(RoutingContext ctx, int statusCode, String message) {
        LOGGER.error("Handling error: {} - {}", statusCode, message);
        ctx.response()
            .setStatusCode(statusCode)
            .putHeader(CONTENT_TYPE, APPLICATION_JSON)
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
                handleError(ctx, HTTP_NOT_FOUND, "Endpoint not found: " + methodName);
                return;
            }

            // Use the shared builder to get the descriptors
            String serviceName = sd.getName();
            ProtoDescriptorBuilder.BuildResult buildResult = ProtoDescriptorBuilder.buildFromProtoDefinition(
                sd.getId(),
                sd.getProtoDefinition()
            );

            Descriptors.FileDescriptor mainFileDescriptor = buildResult.getFileDescriptor();

            // Find the ServiceDescriptor
            Descriptors.ServiceDescriptor serviceDescriptor = mainFileDescriptor.findServiceByName(serviceName);
            if (serviceDescriptor == null) {
                handleError(ctx, HTTP_SERVER_ERROR, "Failed to find service " + serviceName);
                return;
            }

            // Verify the method exists
            if (serviceDescriptor.findMethodByName(endpoint.getMethodName()) == null) {
                handleError(ctx, HTTP_SERVER_ERROR, "Method not found in service: " + endpoint.getMethodName());
                return;
            }

            // Get request body
            JsonObject requestBody = ctx.body().asJsonObject();
            if (requestBody == null) {
                requestBody = new JsonObject(); // Treat empty body as empty JSON
            }

            // Validate basic request
            Descriptors.MethodDescriptor methodDesc = serviceDescriptor.findMethodByName(endpoint.getMethodName());
            if (!requestBody.isEmpty() && endpoint.getInputMapping().isEmpty()) {
                // Only validate fields if we have a request body and no explicit mapping
                for (String fieldName : requestBody.fieldNames()) {
                    if (methodDesc.getInputType().findFieldByName(fieldName) == null) {
                        handleError(ctx, 400, String.format(
                            "Invalid field '%s'. Available fields are: %s",
                            fieldName,
                            methodDesc.getInputType().getFields().stream()
                                .map(Descriptors.FieldDescriptor::getName)
                                .toList()
                        ));
                        return;
                    }
                }
            }

            // Make the gRPC call using DynamicGrpcInvoker
            grpcInvoker.invoke(sd, endpoint.getMethodName(), requestBody)
                .onSuccess(response -> ctx.response()
                    .putHeader(CONTENT_TYPE, APPLICATION_JSON)
                    .end(response.encode()))
                .onFailure(e -> {
                    LOGGER.error("Error processing gRPC request", e);
                    handleError(ctx, HTTP_SERVER_ERROR, "Error processing request: " + e.getMessage());
                });

        } catch (Exception e) {
            LOGGER.error("Error setting up gRPC request", e);
            handleError(ctx, HTTP_SERVER_ERROR, "Error setting up gRPC request: " + e.getMessage());
        }
    }
}
