package com.cresterida.gateway;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import io.vertx.micrometer.Label;
import io.vertx.micrometer.MicrometerMetricsOptions;
import io.vertx.micrometer.VertxPrometheusOptions;
import io.vertx.micrometer.backends.BackendRegistries;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class Main {
    private static final Logger logger = LogManager.getLogger(Main.class);
    public static void main(String args []) {
        String currentLevel = System.getenv("LOG_LEVEL");
        logger.info(currentLevel);


        MicrometerMetricsOptions metricsOptions = new MicrometerMetricsOptions()
                .setEnabled(true)
                .setJvmMetricsEnabled(true)

                .setPrometheusOptions(
                  new VertxPrometheusOptions().setEnabled(true)
                          .setStartEmbeddedServer(false))
                .addLabels(Label.HTTP_METHOD, Label.HTTP_PATH, Label.HTTP_CODE);
        // 5. Create Vertx with the explicit factory. This is the single source of truth.


        Vertx vertx = Vertx.builder()
                .with(new VertxOptions()
                        .setMetricsOptions(metricsOptions))

                .build();

        var registry = BackendRegistries.getDefaultNow();

        if (registry == null) {
            logger.info("No backend registry available");
        }
        else {

            logger.info(registry.getClass().getName());
        }


        // Check if this is the only Vert.x instance
        try {
            Vertx current = Vertx.currentContext() != null ? Vertx.currentContext().owner() : null;
            logger.info( current);
        } catch (Exception e) {
            logger.info("No current Vert.x context");
        }


        // Simple shutdown hook that just closes vertx
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down application...");
            try {
                CountDownLatch latch = new CountDownLatch(1);
                vertx.close()
                        .onComplete(ar -> latch.countDown())
                        .onFailure( err -> logger.error(err.getMessage()));


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
        printLogDetails();
        // Deploy the API Gateway verticle
        vertx.deployVerticle(new ApiGatewayVerticle())
                .onSuccess(id -> logger.info("Gateway started successfully"))
                .onFailure(err -> {
                    logger.error(err);
                    System.exit(1);
                });

    }

    static private void printLogDetails() {

        logger.info("Logger Name: " + logger.getName());
        logger.info("Logger Level: " + logger.getLevel());
        logger.info("Logger Class: " + logger.getClass().getName());
    }
}
