package com.cresterida.gateway.handlers;

import com.cresterida.gateway.registry.ServiceRegistry;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.core.Vertx;

public class ApisServiceHandler {

    private final ServiceRegistry serviceRegistry;
    private final Vertx vertx;

    public ApisServiceHandler(
            ServiceRegistry serviceRegistry,
            Vertx vertx
    ) {
        this.serviceRegistry = serviceRegistry;
        this.vertx = vertx;
    }

    public void registerRoutes(Router router) {
        DynamicGrpcProxyHandler grpcHandler = new DynamicGrpcProxyHandler(vertx);

        router.route("/api/*").handler(ctx -> {
            String servicePath = ctx.request().path();
            var serviceOpt = serviceRegistry.resolveByPath(servicePath);
            if (serviceOpt.isPresent()) {
                ctx.put("service", serviceOpt.get());
                grpcHandler.handle(ctx);
            } else {
                ctx.response()
                    .setStatusCode(404)
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject()
                        .put("error", "No service matches path " + servicePath)
                        .put("status", 404)
                        .put("path", servicePath)
                        .encode());
            }
        });
    }
}
