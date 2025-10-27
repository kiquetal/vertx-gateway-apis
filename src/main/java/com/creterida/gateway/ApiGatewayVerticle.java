package com.createrida.gateway;

import com.example.gateway.model.ServiceDefinition;
import com.example.gateway.ratelimit.TokenBucket;
import com.example.gateway.registry.ServiceRegistry;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;

import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ApiGatewayVerticle extends AbstractVerticle {

  private ServiceRegistry registry;
  private WebClient client;
  private final Map<String, TokenBucket> limiters = new ConcurrentHashMap<>();

  @Override
  public void start(Promise<Void> startPromise) {
    this.registry = new ServiceRegistry();
    this.client = WebClient.create(vertx, new WebClientOptions()
      .setKeepAlive(true)
      .setTcpKeepAlive(true)
      .setTryUseCompression(true)
      .setMaxPoolSize(1024)
      .setHttp2KeepAliveTimeout(30)
      .setPipeliningLimit(64)
    );

    Router router = Router.router(vertx);
    router.route().handler(BodyHandler.create());

    // Admin API
    router.post("/admin/services").handler(this::handleAddService);
    router.get("/admin/services").handler(this::handleListServices);
    router.get("/admin/services/:id").handler(this::handleGetService);
    router.put("/admin/services/:id").handler(this::handleUpdateService);
    router.delete("/admin/services/:id").handler(this::handleDeleteService);

    // Gateway catch-all
    router.route().handler(this::resolveServiceHandler);
    router.route().handler(this::rateLimitHandler);
    router.route().handler(this::proxyHandler);

    int port = getPort();
    HttpServer server = vertx.createHttpServer();
    server.requestHandler(router)
      .listen(port)
      .onSuccess(s -> {
        System.out.println("HTTP server started on port " + port);
        startPromise.complete();
      })
      .onFailure(startPromise::fail);
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

  // ===== Admin Handlers =====

  private void handleAddService(RoutingContext ctx) {
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

  private void handleListServices(RoutingContext ctx) {
    List<ServiceDefinition> list = registry.list();
    JsonArray arr = new JsonArray();
    list.forEach(sd -> arr.add(sd.toJson()));
    ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json").end(arr.encode());
  }

  private void handleGetService(RoutingContext ctx) {
    String id = ctx.pathParam("id");
    registry.getById(id)
      .ifPresentOrElse(sd -> ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json").end(sd.toJson().encode()),
        () -> fail(ctx, 404, "Service not found"));
  }

  private void handleUpdateService(RoutingContext ctx) {
    String id = ctx.pathParam("id");
    try {
      JsonObject body = ctx.body().asJsonObject();
      ServiceDefinition incoming = ServiceDefinition.fromJson(body.put("id", id));
      Optional<ServiceDefinition> updated = registry.update(id, incoming);
      if (updated.isEmpty()) { fail(ctx, 404, "Service not found"); return; }
      ServiceDefinition def = updated.get();
      limiters.put(def.getId(), new TokenBucket(def.getBurstCapacity(), def.getRateLimitPerSecond()));
      ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json").end(def.toJson().encode());
    } catch (Exception e) {
      fail(ctx, 400, e.getMessage());
    }
  }

  private void handleDeleteService(RoutingContext ctx) {
    String id = ctx.pathParam("id");
    Optional<ServiceDefinition> removed = registry.remove(id);
    if (removed.isPresent()) {
      limiters.remove(id);
      ctx.response().setStatusCode(204).end();
    } else {
      fail(ctx, 404, "Service not found");
    }
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
