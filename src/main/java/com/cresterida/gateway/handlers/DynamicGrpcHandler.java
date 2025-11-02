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


}
