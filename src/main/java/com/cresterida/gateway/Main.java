package com.cresterida.gateway;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {

  private static final Logger logger = LoggerFactory.getLogger(Main.class);

  public static void main(String[] args) {
    String currentLevel = System.getenv("LOG_LEVEL");
    logger.info("Current LOG_LEVEL is: {}", (currentLevel != null ? currentLevel : "INFO (default)"));

    Vertx vertx = Vertx.vertx(new VertxOptions());
    vertx.deployVerticle(new ApiGatewayVerticle())
      .onSuccess(id -> {
        logger.info("Gateway started, deploymentId={}", id);
      })
      .onFailure(err -> {
        logger.error("Failed to start gateway: {}", err.getMessage());
        System.exit(1);
      });
  }
}
