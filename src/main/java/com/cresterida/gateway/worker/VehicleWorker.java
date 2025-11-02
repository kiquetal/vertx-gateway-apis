package com.cresterida.gateway.worker;
import com.cresterida.gateway.worker.model.Vehicle;
import com.github.os72.protocjar.Protoc;
import com.google.protobuf.DescriptorProtos;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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


        LOGGER.info("Starting the test to identify proto file");
        String protoContent = """
                "syntax = \\"proto3\\";\\n" +
                                      "package helloworld;\\n" +
                                      "service Greeter {\\n" +
                                      "  rpc SayHello (HelloRequest) returns (HelloReply) {}\\n" +
                                      "}\\n" +
                                      "message HelloRequest {\\n" +
                                      "  string name = 1;\\n" +
                                      "}\\n" +
                                      "message HelloReply {\\n" +
                                      "  string message = 1;\\n" +
                                      "}\\n
                
     
                """;

        Path tempDir  = null;
        Path protoFile = null;
        try {
            tempDir = Files.createTempDirectory("proto-");
            protoFile = tempDir.resolve("helloworld.proto");
            Files.writeString(protoFile, protoContent);
        } catch (IOException e) {
                        throw new RuntimeException(e);

        }
        File descriptorFile = null;
        try {
            descriptorFile = File.createTempFile("descriptor",".desc");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        String[] args = new String[]{
                "--proto_path=" + tempDir.toAbsolutePath(),
                "--descriptor_set_out=" + descriptorFile.getAbsolutePath(),
                protoFile.toAbsolutePath().toString()
        };
        try {
            int exitCode = Protoc.runProtoc(args);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        readBinaryFiles(descriptorFile);

        LOGGER.info("Proto file created at: {}", protoFile.toAbsolutePath());

    }
    private void readBinaryFiles(File descriptorFile) {
        try {
            byte[] fileContent = Files.readAllBytes(descriptorFile.toPath());
            DescriptorProtos.FileDescriptorSet descriptorSet = DescriptorProtos.FileDescriptorSet.parseFrom(fileContent);


            LOGGER.info("Parsed Descriptor Set: {}", descriptorSet);
        } catch (IOException e) {
            LOGGER.error("Error reading descriptor file", e);
        }
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
