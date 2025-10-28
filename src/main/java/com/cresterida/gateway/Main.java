package com.cresterida.gateway;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import io.vertx.micrometer.Label;
import io.vertx.micrometer.MicrometerMetricsOptions;
import io.vertx.micrometer.VertxPrometheusOptions;
import io.vertx.micrometer.backends.BackendRegistries;
import java.util.concurrent.TimeUnit;

public class Main {
    private static final Logger logger = LogManager.getLogger(Main.class);
    private static Vertx vertx;

    public static void main(String args []) {
        configureLogging();

        MicrometerMetricsOptions metricsOptions = new MicrometerMetricsOptions()
                .setEnabled(true)
                .setJvmMetricsEnabled(true)

                .setPrometheusOptions(
                  new VertxPrometheusOptions().setEnabled(true)
                          .setStartEmbeddedServer(false))
                .addLabels(Label.HTTP_METHOD, Label.HTTP_PATH, Label.HTTP_CODE);

        vertx = Vertx.builder()
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
        // Deploy the API Gateway verticle
        vertx.deployVerticle(new ApiGatewayVerticle())
                .onSuccess(id -> logger.info("Gateway started successfully"))
                .onFailure(err -> {
                    logger.error(err);
                    System.exit(1);
                });


            setupShutdownHook(vertx);
        printLogDetails();

    }
    private static void setupShutdownHook(Vertx vertx) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Initiating shutdown sequence...");

            try {
                // First close Vert.x and wait synchronously
                vertx.close()
                        .toCompletionStage()
                        .toCompletableFuture()
                        .get(30, TimeUnit.SECONDS);

                System.out.println("Vert.x shutdown completed");

                // Ensure all loggers are flushed and closed
                LogManager.shutdown(true);

                System.out.println("Shutdown completed");

            } catch (Exception e) {
                System.err.println("Error during shutdown: " + e.getMessage());
                System.exit(1);
            }
        }));
    }

    private static void configureLogging() {
        // Configure global logging level
        String globalLevel = System.getenv("LOG_LEVEL");
        if (globalLevel != null) {
            try {
                Level level = Level.valueOf(globalLevel.toUpperCase());
                Configurator.setRootLevel(level);
                logger.info("Set global logging level to: {}", level);
            } catch (IllegalArgumentException e) {
                logger.warn("Invalid LOG_LEVEL value: {}. Using default.", globalLevel);
            }
        }

        // Configure application-specific logging level
        String appLevel = System.getenv("LOG_LEVEL_APP");
        if (appLevel != null) {
            try {
                Level level = Level.valueOf(appLevel.toUpperCase());
                Configurator.setLevel("com.cresterida.gateway", level);
                logger.info("Set application logging level to: {}", level);
            } catch (IllegalArgumentException e) {
                logger.warn("Invalid LOG_LEVEL_APP value: {}. Using default.", appLevel);
            }
        }
    }





    static private void printLogDetails() {

        logger.info("Logger Name: " + logger.getName());
        logger.info("Logger Level: " + logger.getLevel());
        logger.info("Logger Class: " + logger.getClass().getName());
    }


}
