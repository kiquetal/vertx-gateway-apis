package com.cresterida.gateway.util;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import io.vertx.micrometer.backends.BackendRegistries;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CounterMetrics {
    private static final String DEFAULT_METRIC_NAME = "http_requests_total";
    private static final Counter.Builder counterBuilder;
    private static final Map<String, Counter> taggedCounters = new ConcurrentHashMap<>();

    static {
        MeterRegistry meterRegistry = BackendRegistries.getDefaultNow();
        if (meterRegistry != null) {
            counterBuilder = Counter.builder(DEFAULT_METRIC_NAME)
                    .description("Total HTTP requests processed");
        } else {
            counterBuilder = null;
        }
    }

    public static Handler<RoutingContext> withMetrics(Handler<RoutingContext> handler) {
        return ctx -> {
            if (counterBuilder != null) {
                String path = ctx.request().path();
                String method = ctx.request().method().name();
                String key = method + ":" + path;

                Counter counter = taggedCounters.computeIfAbsent(key, k ->
                    counterBuilder.tags("endpoint", path, "method", method)
                        .register(BackendRegistries.getDefaultNow())
                );
                counter.increment();
            }
            handler.handle(ctx);
        };
    }

    public static Counter getOrCreateCounter(String path, String method) {
        if (counterBuilder == null) {
            return null;
        }
        String key = method + ":" + path;
        return taggedCounters.computeIfAbsent(key, k ->
            counterBuilder.tags("endpoint", path, "method", method)
                .register(BackendRegistries.getDefaultNow())
        );
    }
}
