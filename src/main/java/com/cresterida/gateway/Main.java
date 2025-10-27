package com.cresterida.gateway;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class Main {

  private static final Logger logger = LoggerFactory.getLogger(Main.class);

  // Track deployment id so shutdown can undeploy the verticle first
  private static final AtomicReference<String> deploymentIdRef = new AtomicReference<>();

  // Configurable shutdown timeout (milliseconds)
  private static final long SHUTDOWN_TIMEOUT_MS;
  static {
    long tmp = 10000L; // default 10s
    String t = System.getenv("SHUTDOWN_TIMEOUT_MS");
    if (t != null) {
      try {
        tmp = Long.parseLong(t);
      } catch (NumberFormatException nfe) {
        // keep default
      }
    }
    SHUTDOWN_TIMEOUT_MS = tmp;
  }

  public static void main(String[] args) {
    String currentLevel = System.getenv("LOG_LEVEL");
    logger.info("Current LOG_LEVEL is: {}", (currentLevel != null ? currentLevel : "INFO (default)"));

    Vertx vertx = Vertx.vertx(new VertxOptions());

    // Register a JVM shutdown hook for graceful shutdown (SIGTERM, CTRL+C)
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      logger.info("Shutdown signal received. Initiating graceful shutdown...");
      CountDownLatch latch = new CountDownLatch(1);

      // First try to undeploy the verticle if we have an id
      String id = deploymentIdRef.get();
      if (id != null) {
        logger.info("Undeploying verticle {} before closing Vert.x", id);
        vertx.undeploy(id).onComplete(undeployAr -> {
          if (undeployAr.succeeded()) {
            logger.info("Verticle {} undeployed successfully.", id);
          } else {
            logger.warn("Failed to undeploy verticle {}: {}", id, undeployAr.cause() != null ? undeployAr.cause().getMessage() : "unknown");
          }

          // After undeploy (or failure), close Vert.x
          vertx.close().onComplete(closeAr -> {
            if (closeAr.succeeded()) {
              logger.info("Vert.x closed gracefully.");
            } else {
              logger.error("Error during Vert.x close: {}", closeAr.cause() != null ? closeAr.cause().getMessage() : "unknown");
            }
            latch.countDown();
          });
        });
      } else {
        // No deployment id available; just close Vert.x
        vertx.close().onComplete(ar -> {
          if (ar.succeeded()) {
            logger.info("Vert.x closed gracefully.");
          } else {
            logger.error("Error during Vert.x close: {}", ar.cause() != null ? ar.cause().getMessage() : "unknown");
          }
          latch.countDown();
        });
      }

      try {
        if (!latch.await(SHUTDOWN_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
          logger.warn("Timed out ({}) waiting for Vert.x to close. Forcing exit.", SHUTDOWN_TIMEOUT_MS);
        }
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
        logger.warn("Interrupted while waiting for Vert.x to close.");
      }
    }, "vertx-shutdown-hook"));

    vertx.deployVerticle(new ApiGatewayVerticle())
      .onSuccess(id -> {
        // store the deployment id so shutdown hook can undeploy
        deploymentIdRef.set(id);
        logger.info("Gateway started, deploymentId={}", id);
      })
      .onFailure(err -> {
        logger.error("Failed to start gateway: {}", err.getMessage());
        System.exit(1);
      });
  }
}
