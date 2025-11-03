package com.cresterida.gateway.handlers;

import com.cresterida.gateway.model.ServiceDefinition;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.WebClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.net.URI;

public class HttpProxyHandler implements Handler<RoutingContext> {
    private static final Logger LOGGER = LogManager.getLogger(HttpProxyHandler.class);
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String APPLICATION_JSON = "application/json";

    private final WebClient client;

    public HttpProxyHandler(Vertx vertx) {
        this.client = WebClient.create(vertx);
    }

    @Override
    public void handle(RoutingContext ctx) {
        ServiceDefinition sd = ctx.get("service");
        if (sd == null) {
            ctx.next();
            return;
        }

        try {
            // Parse the upstream URL
            URI upstreamUri = new URI(sd.getUpstreamBaseUrl());
            String path = ctx.request().path();
            
            // Remove prefix if configured
            if (sd.isStripPrefix() && path.startsWith(sd.getPathPrefix())) {
                path = path.substring(sd.getPathPrefix().length());
            }
            
            // Start building the request
            io.vertx.ext.web.client.HttpRequest<io.vertx.core.buffer.Buffer> request = client
                .request(ctx.request().method(), 
                        upstreamUri.getPort(), 
                        upstreamUri.getHost(), 
                        path);

            // Copy headers
            ctx.request().headers().forEach(header -> 
                request.putHeader(header.getKey(), header.getValue()));

            // Send the request
            if (ctx.getBody() != null && !ctx.getBody().isEmpty()) {
                request.sendBuffer(ctx.getBody())
                    .onSuccess(response -> handleResponse(ctx, response))
                    .onFailure(err -> handleError(ctx, err));
            } else {
                request.send()
                    .onSuccess(response -> handleResponse(ctx, response))
                    .onFailure(err -> handleError(ctx, err));
            }

        } catch (Exception e) {
            handleError(ctx, e);
        }
    }

    private void handleResponse(RoutingContext ctx, 
                              io.vertx.ext.web.client.HttpResponse<io.vertx.core.buffer.Buffer> response) {
        // Copy status code
        ctx.response().setStatusCode(response.statusCode());
        
        // Copy headers
        response.headers().forEach(header -> 
            ctx.response().putHeader(header.getKey(), header.getValue()));
        
        // Send response
        if (response.body() != null) {
            ctx.response().end(response.body());
        } else {
            ctx.response().end();
        }
    }

    private void handleError(RoutingContext ctx, Throwable err) {
        LOGGER.error("Error processing HTTP request", err);
        ctx.response()
            .setStatusCode(500)
            .putHeader(CONTENT_TYPE, APPLICATION_JSON)
            .end(new JsonObject()
                .put("error", "Error processing request: " + err.getMessage())
                .put("status", 500)
                .put("path", ctx.request().path())
                .encode());
    }
}
