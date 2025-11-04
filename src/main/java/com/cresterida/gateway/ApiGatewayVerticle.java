package com.cresterida.gateway;

import com.cresterida.gateway.handlers.AdminServiceHandler;
import com.cresterida.gateway.handlers.DynamicGrpcProxyHandler;
import com.cresterida.gateway.handlers.HttpProxyHandler;
import com.cresterida.gateway.model.ServiceType;
import com.cresterida.gateway.ratelimit.TokenBucket;
import com.cresterida.gateway.registry.ServiceRegistry;
import java.util.concurrent.ConcurrentHashMap;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ApiGatewayVerticle extends AbstractVerticle {
    private static final Logger LOGGER = LogManager.getLogger(ApiGatewayVerticle.class);
    private static final int DEFAULT_PORT = 8080;

    private ServiceRegistry registry;
    private AdminServiceHandler adminHandler;
    private DynamicGrpcProxyHandler grpcHandler;
    private HttpProxyHandler httpHandler;
    private final ConcurrentHashMap<String, TokenBucket> rateLimiters = new ConcurrentHashMap<>();

    @Override
    public void start(Promise<Void> startPromise) {
        // Initialize components
        registry = new ServiceRegistry();
        adminHandler = new AdminServiceHandler(registry, rateLimiters);
        grpcHandler = new DynamicGrpcProxyHandler(vertx);
        httpHandler = new HttpProxyHandler(vertx);

        // Create router
        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());

        // Admin routes
        setupAdminRoutes(router);

        // API routes with service type routing
        setupApiRoutes(router);
        LOGGER.debug("API routes set up completed");
        // Start the server
        vertx.createHttpServer()
            .requestHandler(router)
            .listen(config().getInteger("http.port", DEFAULT_PORT))
            .onSuccess(server -> {
                LOGGER.info("Gateway started on port {}", server.actualPort());
                startPromise.complete();
            })
            .onFailure( err -> {;
                LOGGER.error("Failed to start gateway: {}", err.getMessage());
                startPromise.fail(err);
            });
    }

    private void setupAdminRoutes(Router router) {
        router.post("/admin/services").handler(adminHandler::handleAddService);
        router.get("/admin/services").handler(adminHandler::handleListServices);
        router.get("/admin/services/:id").handler(adminHandler::handleGetService);
        router.put("/admin/services/:id").handler(adminHandler::handleUpdateService);
        router.delete("/admin/services/:id").handler(adminHandler::handleDeleteService);
    }

    private void setupApiRoutes(Router router) {
        router.route("/api/*").handler(ctx -> {
            String path = ctx.request().path();

            // Try to resolve the service first
            registry.resolveByPath(path).ifPresentOrElse(service -> {
                // Set service in context for handlers to use
                ctx.put("service", service);

                // Route based on service type
                if (service.getType() == ServiceType.GRPC) {
                    LOGGER.debug("Routing to gRPC handler: {}", path);
                    grpcHandler.handle(ctx);
                } else {
                    LOGGER.debug("Routing to HTTP handler: {}", path);
                    httpHandler.handle(ctx);
                }
            }, () -> {
                // No service found for this path
                LOGGER.warn("No service found for path: {}", path);
                ctx.response()
                    .setStatusCode(404)
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject()
                        .put("error", "No service found for path: " + path)
                        .put("status", 404)
                        .encode());
            });
        });
    }
}
