package com.example.gateway;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;

public class Main {
  public static void main(String[] args) {
    Vertx vertx = Vertx.vertx(new VertxOptions());
    vertx.deployVerticle(new ApiGatewayVerticle())
      .onSuccess(id -> System.out.println("Gateway started, deploymentId=" + id))
      .onFailure(err -> {
        err.printStackTrace();
        System.exit(1);
      });
  }
}
