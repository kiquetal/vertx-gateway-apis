package com.cresterida.gateway;

import com.cresterida.gateway.model.ServiceDefinition;
import com.cresterida.gateway.ratelimit.TokenBucket;
import com.cresterida.gateway.registry.ServiceRegistry;
import com.cresterida.gateway.worker.VehicleWorker;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import io.vertx.core.DeploymentOptions;
import com.cresterida.gateway.handlers.AdminServiceHandler;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.ThreadingModel;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.healthchecks.HealthChecks;
import io.vertx.ext.healthchecks.Status;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.healthchecks.HealthCheckHandler;
import io.vertx.micrometer.MicrometerMetricsOptions;
import io.vertx.micrometer.VertxPrometheusOptions;
import io.vertx.micrometer.backends.BackendRegistries;
import io.vertx.micrometer.PrometheusScrapingHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ApiGatewayVerticle extends AbstractVerticle {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApiGatewayVerticle.class);
    private ServiceRegistry registry;
    private WebClient client;
    private HttpServer httpServer;
    private final Map<String, TokenBucket> limiters = new ConcurrentHashMap<>();
    private AdminServiceHandler adminHandler;

  @Override
  public void start(Promise<Void> startPromise) {

    this.registry = new ServiceRegistry();
    this.client = WebClient.create(vertx, new WebClientOptions()
      .setKeepAlive(true)
      .setTcpKeepAlive(true)
      .setHttp2KeepAliveTimeout(30)
      .setPipeliningLimit(64)
    );

    // Deploy VehicleWorker with virtual threads
    DeploymentOptions options = new DeploymentOptions()
            .setThreadingModel(ThreadingModel.WORKER);
    vertx.deployVerticle(new VehicleWorker(), options)
      .onSuccess(id -> LOGGER.info("VehicleWorker deployed with id: {}", id))
      .onFailure(err -> {
        System.err.println("Failed to deploy VehicleWorker: " + err.getMessage());
        startPromise.fail(err);
      });

    this.adminHandler = new AdminServiceHandler(registry, limiters);


      HealthChecks hc = HealthChecks.create(vertx);
      HealthCheckHandler healthCheckHandler = HealthCheckHandler.createWithHealthChecks(hc);

      hc.register("application-ready", promise -> {
          // Here you can add checks to verify if the application is ready
          promise.complete(Status.OK());
      });


    Router router = Router.router(vertx);
    router.route().handler(BodyHandler.create());


    router.get("/health/ready").handler(healthCheckHandler);
    // Metrics endpoint
    router.get("/metrics").handler(PrometheusScrapingHandler.create());
    // Admin API routes
    router.post("/admin/services").handler(adminHandler::handleAddService);
    router.get("/admin/services").handler(adminHandler::handleListServices);
    router.get("/admin/services/:id").handler(adminHandler::handleGetService);
    router.put("/admin/services/:id").handler(adminHandler::handleUpdateService);
    router.delete("/admin/services/:id").handler(adminHandler::handleDeleteService);
    // Test route for VehicleWorker
    router.post("/test/vehicle").handler(ctx -> {
      JsonObject vehicle = ctx.body().asJsonObject();
      if (vehicle == null) {
        fail(ctx, 400, "Invalid vehicle data");
        return;
      }

      // Respond immediately with acceptance
      JsonObject immediateResponse = new JsonObject()
          .put("message", "Vehicle processing started")
          .put("id", vehicle.getString("id"))
          .put("status", "ACCEPTED");

      // Send to VehicleWorker through event bus (fire and forget)
      vertx.eventBus().send("vehicle.process", vehicle);

      ctx.response()
          .putHeader("Content-Type", "application/json")
          .end(immediateResponse.encode());
    });

    // Gateway catch-all
    router.route().handler(this::resolveServiceHandler);
    router.route().handler(this::rateLimitHandler);
    router.route().handler(this::proxyHandler);

    int port = getPort();
    this.httpServer = vertx.createHttpServer();
    this.httpServer.requestHandler(router)
      .listen(port)
      .onSuccess(s -> {
        this.httpServer = s;
        LOGGER.info("HTTP server started on port {}", port);
        startPromise.complete();
      })
      .onFailure(startPromise::fail);
  }

  @Override
  public void stop(Promise<Void> stopPromise) {
    LOGGER.info("ApiGatewayVerticle stopping: initiating graceful shutdown...");

    // First close the WebClient to stop new requests
    if (client != null) {
      try {
        client.close();
        LOGGER.info("WebClient closed successfully");
      } catch (Exception e) {
        LOGGER.warn("WebClient close exception: {}", e.getMessage());
      }
    }

    // Then close HTTP server to stop accepting new requests
    if (httpServer != null) {
      httpServer.close().onComplete(ar -> {
        if (ar.succeeded()) {
          LOGGER.info("HTTP server closed successfully");
        } else {
          LOGGER.warn("HTTP server close error: {}", ar.cause() != null ? ar.cause().getMessage() : "unknown");
        }
        LOGGER.info("ApiGatewayVerticle shutdown complete");
        stopPromise.complete();
      });
    } else {
      LOGGER.info("ApiGatewayVerticle shutdown complete (no HTTP server was running)");
      stopPromise.complete();
    }
  }


  private int getPort() {
    String env = System.getenv("PORT");
    if (env != null) {
      try { return Integer.parseInt(env); } catch (Exception ignored) {}
    }
    String prop = System.getProperty("http.port");
    if (prop != null) {
      try { return Integer.parseInt(prop); } catch (Exception ignored) {}
    }
    return 8080;
  }

  // ===== Gateway Handlers =====

  private void resolveServiceHandler(RoutingContext ctx) {
    String path = ctx.request().path();
    Optional<ServiceDefinition> match = registry.resolveByPath(path);
    if (match.isEmpty()) {
      fail(ctx, 404, "No service matches path " + path);
      return;
    }
    ctx.put("service", match.get());
    ctx.next();
  }

  private void rateLimitHandler(RoutingContext ctx) {
    ServiceDefinition sd = ctx.get("service");
    if (sd == null) { ctx.next(); return; }
    TokenBucket bucket = limiters.computeIfAbsent(sd.getId(), id -> new TokenBucket(sd.getBurstCapacity(), sd.getRateLimitPerSecond()));
    if (!bucket.tryConsume()) {
      fail(ctx, 429, "Rate limit exceeded for service: " + sd.getName());
      return;
    }
    ctx.next();
  }

  private void proxyHandler(RoutingContext ctx) {
    ServiceDefinition sd = ctx.get("service");
    if (sd == null) { ctx.next(); return; }

    // Store start time in context for logging
    ctx.put("startTime", System.nanoTime());
    String incomingPath = ctx.request().path();
    String pathToUpstream;
    if (sd.isStripPrefix()) {
      String prefix = sd.getPathPrefix();
      if (incomingPath.equals(prefix)) {
        pathToUpstream = "/"; // root
      } else if (incomingPath.startsWith(prefix + "/")) {
        pathToUpstream = incomingPath.substring(prefix.length());
      } else {
        // shouldn't happen due to match
        pathToUpstream = incomingPath;
      }
    } else {
      pathToUpstream = incomingPath;
    }

    // Build upstream URI
    URI base = URI.create(sd.getUpstreamBaseUrl());
    String fullPath = normalizePath(base.getPath(), pathToUpstream);

    HttpMethod method = ctx.request().method();
    HttpRequest<Buffer> req = client.request(method, base.getPort() == -1 ? ("https".equalsIgnoreCase(base.getScheme()) ? 443 : 80) : base.getPort(), base.getHost(), fullPath)
      .ssl("https".equalsIgnoreCase(base.getScheme()));

    // Copy query params
    ctx.queryParams().forEach(entry -> req.addQueryParam(entry.getKey(), entry.getValue()));

    // Copy headers (filter hop-by-hop)
    ctx.request().headers().forEach(h -> {
      String key = h.getKey();
      if (isHopByHopHeader(key)) return;
      req.putHeader(key, h.getValue());
    });
    // Override Host header to upstream's host
    req.putHeader("Host", base.getHost());

    // Send body if present
    Buffer body = ctx.body().buffer();
    if (body != null) {
      req.sendBuffer(body).onComplete(ar -> handleProxyResult(ctx, ar));
    } else {
      req.send().onComplete(ar -> handleProxyResult(ctx, ar));
    }
  }

  private void handleProxyResult(RoutingContext ctx, io.vertx.core.AsyncResult<HttpResponse<Buffer>> ar) {
    if (ar.failed()) {
      fail(ctx, 502, "Upstream error: " + ar.cause().getMessage());
      return;
    }
    HttpResponse<Buffer> resp = ar.result();
    // Copy status and headers
    ctx.response().setStatusCode(resp.statusCode());
    resp.headers().forEach(h -> {
      if (!isHopByHopHeader(h.getKey())) {
        ctx.response().putHeader(h.getKey(), h.getValue());
      }
    });
    // Write body
    Buffer body = resp.bodyAsBuffer();
    if (body == null) {
      ctx.response().end();
    } else {
      ctx.response().end(body);
    }
  }

  private static boolean isHopByHopHeader(String key) {
    String k = key.toLowerCase(Locale.ROOT);
    return k.equals("connection") || k.equals("keep-alive") || k.equals("transfer-encoding") || k.equals("te") || k.equals("trailer") || k.equals("proxy-authorization") || k.equals("proxy-authenticate") || k.equals("upgrade");
  }

  private static String normalizePath(String basePath, String path) {
    String a = (basePath == null || basePath.isBlank()) ? "" : basePath;
    String b = (path == null) ? "" : path;
    if (!a.startsWith("/")) a = "/" + a;
    if (a.endsWith("/")) a = a.substring(0, a.length() - 1);
    if (!b.startsWith("/")) b = "/" + b;
    return a + b;
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
