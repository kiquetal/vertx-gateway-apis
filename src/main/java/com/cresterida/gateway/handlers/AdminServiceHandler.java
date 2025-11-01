package com.cresterida.gateway.handlers;

import com.cresterida.gateway.model.ServiceDefinition;
import com.cresterida.gateway.ratelimit.TokenBucket;
import com.cresterida.gateway.registry.ServiceRegistry;
import com.cresterida.gateway.util.CounterMetrics;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class AdminServiceHandler {
    private final ServiceRegistry registry;
    private final Map<String, TokenBucket> limiters;

    private Logger logger = LoggerFactory.getLogger(AdminServiceHandler.class);
    public AdminServiceHandler(ServiceRegistry registry, Map<String, TokenBucket> limiters) {
        this.registry = registry;
        this.limiters = limiters;
    }

    public Handler<RoutingContext> withIncrementCounter(Handler<RoutingContext> handler) {
        return CounterMetrics.withMetrics(handler);
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
        logger.info("Fetching service with id: {}", id);

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

    public  void registerRoutes(Router router)
    {
        router.post("/admin/services").handler(this::handleListServices);
        router.get("/admin/services").handler(CounterMetrics.withMetrics(this::handleListServices));
        router.get("/admin/services/:id").handler(CounterMetrics.withMetrics(this::handleGetService));
        router.put("/admin/services/:id").handler(this::handleUpdateService);
        router.delete("/admin/services/:id").handler(this::handleDeleteService);


    }
}

