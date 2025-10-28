package com.cresterida.gateway.util;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import io.vertx.micrometer.backends.BackendRegistries;
import java.util.Arrays;

public class CounterMetrics {
    private static final String DEFAULT_METRIC_NAME = "http_requests_total";
    private static final MeterRegistry registry;

    static {
        registry = BackendRegistries.getDefaultNow();
    }

    public static Handler<RoutingContext> withMetrics(Handler<RoutingContext> handler) {
        return ctx -> {
            if (registry != null) {
                // Call the original handler first
                handler.handle(ctx);

                // After handler completes, we can get the status code
                String path = ctx.request().path();
                String method = ctx.request().method().name();
                String status = String.valueOf(ctx.response().getStatusCode());

                // Create the counter with all three tags
                registry.counter(DEFAULT_METRIC_NAME,
                    Arrays.asList(
                        Tag.of("endpoint", path),
                        Tag.of("method", method),
                        Tag.of("status", status)
                    )).increment();
            } else {
                handler.handle(ctx);
            }
        };
    }
}
