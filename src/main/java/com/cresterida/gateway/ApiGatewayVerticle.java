package com.cresterida.gateway;

import com.cresterida.gateway.registry.ServiceRegistry;
import com.cresterida.gateway.ratelimit.TokenBucket;
import com.cresterida.gateway.worker.VehicleWorker;
import io.micrometer.core.instrument.Counter;
import io.vertx.core.DeploymentOptions;
import com.cresterida.gateway.handlers.AdminServiceHandler;
import com.cresterida.gateway.handlers.ApisServiceHandler;
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
import io.vertx.micrometer.backends.BackendRegistries;
import io.vertx.micrometer.PrometheusScrapingHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ApiGatewayVerticle extends AbstractVerticle {
    private static final Logger LOGGER = LogManager.getLogger(ApiGatewayVerticle.class);
    private ServiceRegistry registry;
    private WebClient client;
    private HttpServer httpServer;
    private final Map<String, TokenBucket> limiters = new ConcurrentHashMap<>();
    private AdminServiceHandler adminHandler;
    private ApisServiceHandler apisHandler;
    private Counter requestCounter;

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

    HealthChecks hc = HealthChecks.create(vertx);
    HealthCheckHandler healthCheckHandler = HealthCheckHandler.createWithHealthChecks(hc);

    hc.register("application-ready", promise -> {
        // Here you can add checks to verify if the application is ready
        promise.complete(Status.OK());
    });

    this.adminHandler = new AdminServiceHandler(registry, limiters);
    this.apisHandler = new ApisServiceHandler(registry);

    Router router = Router.router(vertx);
    router.route().handler(BodyHandler.create());

    // Health and metrics endpoints
    router.get("/health/ready").handler(healthCheckHandler);
    router.get("/metrics").handler(PrometheusScrapingHandler.create());

    // Register API routes before admin routes
    apisHandler.registerRoutes(router);

    // Admin API routes
    adminHandler.registerRoutes(router);

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

    // Initialize metrics before setting up gateway handlers
    initializeMetrics();
    LOGGER.info("Initializing metrics and handlers...");

    LOGGER.trace("HERE 1");
    // Note: API routes are now handled by ApisServiceHandler

    int port = getPort();
    this.httpServer = vertx.createHttpServer();
    this.httpServer.requestHandler(router)
      .listen(port)
      .onSuccess(s -> {
        this.httpServer = s;
        LOGGER.debug("HTTP server started on port {}", port);
        startPromise.complete();
      })
      .onFailure(startPromise::fail);
  }


    @Override
    public void stop(Promise<Void> stopPromise) {
        System.out.println("ApiGatewayVerticle stopping: initiating graceful shutdown...");

        // Close WebClient first
        if (client != null) {
            try {
                System.out.println("Closing WebClient...");
                client.close();
                System.out.println("WebClient closed successfully");
            } catch (Exception e) {
                System.err.println("WebClient close exception: " + e.getMessage());
            }
        } else {
            System.out.println("No WebClient to close");
        }

        // Then close HTTP server
        if (httpServer != null) {
            System.out.println("Closing HTTP server on port " + getPort() + "...");
            httpServer.close()
                    .onComplete(ar -> {
                if (ar.succeeded()) {
                    System.out.println("HTTP server closed successfully");
                } else {
                    System.err.println("HTTP server close error: " +
                        (ar.cause() != null ? ar.cause().getMessage() : "unknown"));
                }
                System.out.println("ApiGatewayVerticle shutdown complete - completing stop promise");
                stopPromise.complete(); // CRITICAL: This must be called!
            });
        } else {
            System.out.println("ApiGatewayVerticle shutdown complete (no HTTP server was running) - completing stop promise");
            stopPromise.complete(); // CRITICAL: This must be called!
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

  private void initializeMetrics() {
      var registry = BackendRegistries.getDefaultNow();
      if (registry != null) {
          requestCounter = Counter.builder("api_gateway_requests_total")
                  .description("Total number of requests processed")
                  .tag("component", "gateway")
                  .register(registry);
          LOGGER.info("Metrics initialized successfully");
      } else {
          LOGGER.error("Failed to initialize metrics: registry is null");
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
