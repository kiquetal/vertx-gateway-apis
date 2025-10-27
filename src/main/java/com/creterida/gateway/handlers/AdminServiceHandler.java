package com.creterida.gateway.handlers;

import com.creterida.gateway.model.ServiceDefinition;
import com.creterida.gateway.ratelimit.TokenBucket;
import com.creterida.gateway.registry.ServiceRegistry;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class AdminServiceHandler {
    private final ServiceRegistry registry;
    private final Map<String, TokenBucket> limiters;

    public AdminServiceHandler(ServiceRegistry registry, Map<String, TokenBucket> limiters) {
        this.registry = registry;
        this.limiters = limiters;
    }

    public void handleAddService(RoutingContext ctx) {
        try {
            JsonObject body = ctx.body().asJsonObject();
            ServiceDefinition def = ServiceDefinition.fromJson(body);
            registry.add(def);
            limiters.put(def.getId(), new TokenBucket(def.getBurstCapacity(), def.getRateLimitPerSecond()));
            ctx.response().setStatusCode(201)
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end(def.toJson().encode());
        } catch (Exception e) {
            fail(ctx, 400, e.getMessage());
        }
    }

    public void handleListServices(RoutingContext ctx) {
        List<ServiceDefinition> list = registry.list();
        JsonArray arr = new JsonArray();
        list.forEach(sd -> arr.add(sd.toJson()));
        ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json").end(arr.encode());
    }

    public void handleGetService(RoutingContext ctx) {
        String id = ctx.pathParam("id");
        registry.getById(id)
                .ifPresentOrElse(sd -> ctx.response()
                        .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                        .end(sd.toJson().encode()),
                    () -> fail(ctx, 404, "Service not found"));
    }

    public void handleUpdateService(RoutingContext ctx) {
        String id = ctx.pathParam("id");
        try {
            JsonObject body = ctx.body().asJsonObject();
            ServiceDefinition incoming = ServiceDefinition.fromJson(body.put("id", id));
            Optional<ServiceDefinition> updated = registry.update(id, incoming);
            if (updated.isEmpty()) {
                fail(ctx, 404, "Service not found");
                return;
            }
            ServiceDefinition def = updated.get();
            limiters.put(def.getId(), new TokenBucket(def.getBurstCapacity(), def.getRateLimitPerSecond()));
            ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json").end(def.toJson().encode());
        } catch (Exception e) {
            fail(ctx, 400, e.getMessage());
        }
    }

    public void handleDeleteService(RoutingContext ctx) {
        String id = ctx.pathParam("id");
        Optional<ServiceDefinition> removed = registry.remove(id);
        if (removed.isPresent()) {
            limiters.remove(id);
            ctx.response().setStatusCode(204).end();
        } else {
            fail(ctx, 404, "Service not found");
        }
    }

    private void fail(RoutingContext ctx, int status, String message) {
        JsonObject err = new JsonObject()
                .put("error", message)
                .put("status", status)
                .put("path", ctx.request().path());
        ctx.response().setStatusCode(status)
                .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .end(err.encode());
    }
}

