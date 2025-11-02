package com.cresterida.gateway.handlers;

import com.cresterida.gateway.registry.ServiceRegistry;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;

public class ApisServiceHandler {

    private  ServiceRegistry serviceRegistry;

    public ApisServiceHandler(
            ServiceRegistry serviceRegistry
    ) {
        this.serviceRegistry = serviceRegistry;
    }



    public void registerRoutes(Router router) {
        DynamicGrpcProxyHandler grpcHandler = new DynamicGrpcProxyHandler();

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
