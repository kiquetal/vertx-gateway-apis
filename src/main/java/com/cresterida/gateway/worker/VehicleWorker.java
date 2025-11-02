package com.cresterida.gateway.worker;

import com.cresterida.gateway.util.ClientCalls;
import com.cresterida.gateway.worker.model.Vehicle;
import com.github.os72.protocjar.Protoc;
import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.Message;
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
syntax = "proto3";
package helloworld;

service Greeter {
    rpc SayHello (HelloRequest) returns (HelloReply) {}
}

message HelloRequest {
    string name = 1;
}

message HelloReply {
    string message = 1;
}
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
        LOGGER.info("Reading descriptor file from: {}", descriptorFile.getAbsolutePath());
        try {
            byte[] fileContent = Files.readAllBytes(descriptorFile.toPath());
            LOGGER.info("File content size: {} bytes", fileContent.length);
            DescriptorProtos.FileDescriptorSet descriptorSet = DescriptorProtos.FileDescriptorSet.parseFrom(fileContent);
            LOGGER.info("Reading descriptor set from: {}", descriptorSet.getFileList());

            extractServiceDependencies(descriptorSet);

            // Get the HelloRequest message descriptor
            DescriptorProtos.FileDescriptorProto fileProto = descriptorSet.getFile(0);
            DescriptorProtos.DescriptorProto messageType = fileProto.getMessageType(0);

            // Create dynamic message builder for HelloRequest
            DynamicMessage.Builder builder = DynamicMessage.newBuilder(messageType);

            // Find the 'name' field descriptor and set its value
            DescriptorProtos.FieldDescriptorProto nameField = messageType.getField(0);
            String fieldName = nameField.getName();
            LOGGER.info("Setting field '{}' in request", fieldName);

            builder.setField(
                builder.getDescriptorForType().findFieldByName(fieldName),
                "Here from vertx!!!"
            );

            // Build the message and log it
            DynamicMessage request = builder.build();
            LOGGER.info("Created request message: {}", request.toString());

            // Make the gRPC call
            try {
                String fullServiceName = fileProto.getPackage() + ".Greeter";
                LOGGER.info("Making gRPC call to service: {}", fullServiceName);

                Message response = ClientCalls.makeUnaryCall(
                    "localhost",
                    50051,
                    fullServiceName,
                    "SayHello",
                    request,
                    fileProto
                );

                LOGGER.info("Received response: {}", response.toString());
            } catch (Exception e) {
                LOGGER.error("Error making gRPC call: {}", e.getMessage(), e);
                throw new RuntimeException("Failed to make gRPC call", e);
            }

        } catch (IOException e) {
            LOGGER.error("Error reading descriptor file: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to read descriptor file", e);
        } finally {
            descriptorFile.delete();
        }
    }
    private void extractServiceDependencies(DescriptorProtos.FileDescriptorSet descriptorSet) {
        for (DescriptorProtos.FileDescriptorProto fileProto : descriptorSet.getFileList()) {
            // Log basic file info
            LOGGER.info("Package: {}", fileProto.getPackage());
            LOGGER.info("Dependencies: {}", fileProto.getDependencyList());

            // Extract services
            for (DescriptorProtos.ServiceDescriptorProto service : fileProto.getServiceList()) {
                LOGGER.info("Service name: {}", service.getName());

                // Extract methods
                for (DescriptorProtos.MethodDescriptorProto method : service.getMethodList()) {
                    LOGGER.info("Method: {} -> Input: {}, Output: {}",
                            method.getName(),
                            method.getInputType(),
                            method.getOutputType()
                    );
                }
            }

            // Extract message types
            for (DescriptorProtos.DescriptorProto message : fileProto.getMessageTypeList()) {
                LOGGER.info("Message type: {}", message.getName());
                for (DescriptorProtos.FieldDescriptorProto field : message.getFieldList()) {
                    LOGGER.info("Field: {} (type: {})",
                            field.getName(),
                            field.getType()
                    );
                }
            }
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
