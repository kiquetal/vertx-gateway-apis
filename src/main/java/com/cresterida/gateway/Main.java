package com.cresterida.gateway;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        String currentLevel = System.getenv("LOG_LEVEL");
        logger.info("Current LOG_LEVEL is: {}", (currentLevel != null ? currentLevel : "INFO (default)"));

        Vertx vertx = Vertx.vertx(new VertxOptions());
        // Simple shutdown hook that just closes vertx
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down application...");
            try {
                CountDownLatch latch = new CountDownLatch(1);
                vertx.close()
                    .onComplete(ar -> latch.countDown()
                              )
                        .onFailure( err -> logger.error("Error during Vert.x shutdown: {}", err.getMessage())) ;

                // Wait for completion with a reasonable timeout
                if (!latch.await(30, TimeUnit.SECONDS)) {
                    logger.warn("Shutdown timed out after 30 seconds");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Shutdown interrupted");
            }
            logger.info("Shutdown complete");
        }));

        // Deploy the API Gateway verticle
        vertx.deployVerticle(new ApiGatewayVerticle())
            .onSuccess(id -> logger.info("Gateway started successfully"))
            .onFailure(err -> {
                logger.error("Failed to start gateway: {}", err.getMessage());
                System.exit(1);
            });
    }
}
