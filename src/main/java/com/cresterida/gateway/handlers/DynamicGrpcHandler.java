package com.cresterida.gateway.handlers;

import com.cresterida.gateway.util.DynamicMessageBuilder;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.Message;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

public class DynamicGrpcHandler {
    private final DynamicMessageBuilder messageBuilder;

    public DynamicGrpcHandler() {
        this.messageBuilder = new DynamicMessageBuilder();
    }

    public Future<Message> handleRequest(Descriptors.Descriptor descriptor, Object jsonInput) {
        try {
            // Automatically handles both single objects and lists
            Message message = messageBuilder.buildMessage(descriptor, jsonInput);
            return Future.succeededFuture(message);
        } catch (Exception e) {
            return Future.failedFuture(e);
        }
    }

    // Example usage:
    public void example() {
        // For a single object
        JsonObject userJson = new JsonObject()
            .put("name", "John")
            .put("age", 30);
        
        // The same handler works for both cases
        handleRequest(userProfileDescriptor, userJson)
            .onSuccess(message -> {
                // Process the message
            });

        // For a list of items
        JsonArray itemsJson = new JsonArray()
            .add(new JsonObject().put("id", "1"))
            .add(new JsonObject().put("id", "2"));
        
        handleRequest(itemListDescriptor, itemsJson)
            .onSuccess(message -> {
                // Process the message
            });
    }
}
