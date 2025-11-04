package com.cresterida.gateway.worker;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


import java.util.UUID;
import java.util.concurrent.CountDownLatch;
public class VehicleWorkerTest {
    private static final Logger LOGGER = LogManager.getLogger(VehicleWorkerTest.class);
    public static void main(String[] args) throws InterruptedException {
        Vertx vertx = Vertx.vertx();
        CountDownLatch latch = new CountDownLatch(5); // Process 5 vehicles
        // Deploy the VehicleWorker verticle
        vertx.deployVerticle(new VehicleWorker())
                .onComplete(ar -> {
            if (ar.succeeded()) {
                LOGGER.info("VehicleWorker deployed successfully");
                // Process multiple vehicles concurrently
                for (int i = 0; i < 5; i++) {
                    JsonObject vehicle = new JsonObject()
                        .put("id", UUID.randomUUID().toString())
                        .put("make", "Toyota")
                        .put("model", "Camry")
                        .put("year", 2025);
                    vertx.eventBus().<JsonObject>request("vehicle.process", vehicle)
                            .onSuccess(reply -> {
                            JsonObject processedVehicle = reply.body();
                            LOGGER.info("Vehicle processed: {}", processedVehicle.encode());
                                latch.countDown();
                          })
                            .onFailure(err -> {
                        LOGGER.error("Failed to send vehicle for processing: {}", err.getMessage());
                        latch.countDown();
                    });
                }
            } else {
                LOGGER.error("Failed to deploy VehicleWorker", ar.cause());
            }
        });
        // Wait for all vehicles to be processed
        latch.await();
        LOGGER.info("All vehicles processed. Shutting down...");
        vertx.close();
    }
}
