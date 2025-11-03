package com.cresterida.gateway.handlers;

import com.cresterida.gateway.model.ServiceDefinition;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.net.URI;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeoutException;

public class HttpProxyHandler implements Handler<RoutingContext> {
    private record ErrorResponse(int statusCode, String userMessage, String logMessage) {}

    private static final Logger LOGGER = LogManager.getLogger(HttpProxyHandler.class);
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String APPLICATION_JSON = "application/json";

    private final WebClient client;

    public HttpProxyHandler(Vertx vertx) {
        WebClientOptions options = new WebClientOptions()
            .setConnectTimeout(5000)  // 5 seconds
            .setIdleTimeout(60)       // 1 minute
            .setKeepAlive(true);
        this.client = WebClient.create(vertx, options);
    }

    @Override
    public void handle(RoutingContext ctx) {
        ServiceDefinition sd = ctx.get("service");
        if (sd == null) {
            ctx.next();
            return;
        }

        try {
            // Parse the upstream URL and validate
            String upstreamUrl = sd.getUpstreamBaseUrl();
            if (upstreamUrl == null || upstreamUrl.isEmpty()) {
                throw new IllegalStateException("Upstream URL is not configured for service: " + sd.getId());
            }

            URI upstreamUri = new URI(upstreamUrl);
            String host = upstreamUri.getHost();
            int port = upstreamUri.getPort();
            if (port == -1) {
                port = upstreamUri.getScheme().equalsIgnoreCase("https") ? 443 : 80;
            }

            // Build the path
            String path = ctx.request().path();
            if (sd.isStripPrefix() && path.startsWith(sd.getPathPrefix())) {
                path = path.substring(sd.getPathPrefix().length());
            }
            if (!path.startsWith("/")) {
                path = "/" + path;
            }

            LOGGER.debug("Proxying request to {}:{}{}", host, port, path);

            // Start building the request
            io.vertx.ext.web.client.HttpRequest<io.vertx.core.buffer.Buffer> request = client
                .request(ctx.request().method(), port, host, path);

            // Copy original request headers, excluding hop-by-hop headers
            ctx.request().headers().forEach(header -> {
                String headerName = header.getKey();
                String headerValue = header.getValue();
                if (!isHopByHopHeader(headerName)) {
                    request.putHeader(headerName, headerValue);
                }
            });

            // Send the request with or without body
            if (ctx.body().buffer() != null && ctx.body().buffer().length() > 0) {
                LOGGER.debug("Sending request with body of size: {}", ctx.body().buffer().length());
                request.sendBuffer(ctx.body().buffer())
                    .onSuccess(response -> handleResponse(ctx, response))
                    .onFailure(err -> handleError(ctx, err));
            } else {
                LOGGER.debug("Sending request without body");
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

    private boolean isHopByHopHeader(String headerName) {
        headerName = headerName.toLowerCase();
        return headerName.equals("connection") ||
               headerName.equals("keep-alive") ||
               headerName.equals("proxy-authenticate") ||
               headerName.equals("proxy-authorization") ||
               headerName.equals("te") ||
               headerName.equals("trailer") ||
               headerName.equals("transfer-encoding") ||
               headerName.equals("upgrade");
    }

    private void handleError(RoutingContext ctx, Throwable err) {
        // Determine error type and appropriate response
        ErrorResponse response = switch (err) {
            case ConnectException ignored -> new ErrorResponse(
                502, "Unable to connect to upstream service",
                "Connection failed to upstream service");

            case TimeoutException ignored -> new ErrorResponse(
                504, "Upstream service timed out",
                "Request timed out to upstream service");

            case UnknownHostException ignored -> new ErrorResponse(
                502, "Unable to resolve upstream host",
                "Failed to resolve upstream host");

            case IllegalStateException e when e.getMessage().contains("Upstream URL is not configured") ->
                new ErrorResponse(503, e.getMessage(), "Service configuration error");

            default -> new ErrorResponse(
                500, "Internal proxy error",
                "Unexpected error in proxy");
        };

        // Log the error with appropriate message
        LOGGER.error(response.logMessage(), err);

        JsonObject jsonResponse = new JsonObject()
            .put("error", response.userMessage())
            .put("status", response.statusCode())
            .put("path", ctx.request().path());

        if (err.getMessage() != null) {
            jsonResponse.put("detail", err.getMessage());
        }

        ctx.response()
            .setStatusCode(response.statusCode())
            .putHeader(CONTENT_TYPE, APPLICATION_JSON)
            .end(jsonResponse.encode());
    }
}
