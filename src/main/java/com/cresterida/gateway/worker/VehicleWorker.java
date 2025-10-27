package com.cresterida.gateway.worker;
import com.cresterida.gateway.worker.model.Vehicle;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
public class VehicleWorker extends AbstractVerticle {
    private static final Logger LOGGER = LoggerFactory.getLogger(VehicleWorker.class);
    @Override
    public void start(Promise<Void> startPromise) {
        vertx.eventBus().<JsonObject>consumer("vehicle.process", message -> {
            JsonObject vehicleJson = message.body();
            Vehicle vehicle = new Vehicle(
                vehicleJson.getString("id"),
                vehicleJson.getString("make"),
                vehicleJson.getString("model"),
                vehicleJson.getInteger("year")
            );
            processVehicle(vehicle, message);
        });
        LOGGER.info("VehicleWorker started with virtual threads");
        startPromise.complete();
    }
    private void processVehicle(Vehicle vehicle, io.vertx.core.eventbus.Message<JsonObject> message) {
        long startTime = System.currentTimeMillis();
        try {
            LOGGER.info("Started processing vehicle: {}", vehicle.getId());
            vehicle.setStatus("PROCESSING");
            // Use Vert.x timer instead of Thread.sleep
            vertx.setTimer(5000, timerId -> {
                vehicle.setStatus("COMPLETED");
                long processingTime = System.currentTimeMillis() - startTime;
                vehicle.setProcessingTime(processingTime);
                LOGGER.info("Completed processing vehicle: {} in {}ms", vehicle.getId(), processingTime);
                // Create response manually instead of using mapFrom
                JsonObject response = new JsonObject()
                    .put("id", vehicle.getId())
                    .put("make", vehicle.getMake())
                    .put("model", vehicle.getModel())
                    .put("year", vehicle.getYear())
                    .put("status", vehicle.getStatus())
                    .put("processingTime", vehicle.getProcessingTime());
                message.reply(response);
            });
        } catch (Exception e) {
            LOGGER.error("Error processing vehicle: {}", vehicle.getId(), e);
            vehicle.setStatus("ERROR");
            message.fail(500, "Processing failed: " + e.getMessage());
        }
    }
}
