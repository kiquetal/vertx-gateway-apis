package com.cresterida.gateway.util;

import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class DynamicMessageBuilderTest {
    private DynamicMessageBuilder messageBuilder;
    private Descriptors.Descriptor userProfileDescriptor;
    private Descriptors.Descriptor itemListDescriptor;

    @BeforeEach
    void setUp() {
        messageBuilder = new DynamicMessageBuilder();
        // Note: You'll need to load these descriptors from your proto files
        // This is just example test code
    }

    @Test
    void testSingleObjectMapping() {
        // Example JSON for a user profile
        JsonObject userJson = new JsonObject()
            .put("id", "user123")
            .put("name", "John Doe")
            .put("age", 30)
            .put("interests", new JsonArray().add("reading").add("travel"))
            .put("address", new JsonObject()
                .put("street", "123 Main St")
                .put("city", "Springfield")
                .put("country", "USA")
            );

        Message message = messageBuilder.buildMessage(userProfileDescriptor, userJson);
        assertNotNull(message);
        // Add more specific assertions based on your proto definition
    }

    @Test
    void testListMapping() {
        // Example JSON for a list of items
        JsonArray itemsJson = new JsonArray()
            .add(new JsonObject()
                .put("id", "item1")
                .put("name", "Product A")
                .put("price", 29.99)
                .put("tags", new JsonArray().add("electronics").add("sale"))
            )
            .add(new JsonObject()
                .put("id", "item2")
                .put("name", "Product B")
                .put("price", 49.99)
                .put("tags", new JsonArray().add("appliance"))
            );

        Message message = messageBuilder.buildMessage(itemListDescriptor, itemsJson);
        assertNotNull(message);
        // Add more specific assertions based on your proto definition
    }

    @Test
    void testEmptyObject() {
        JsonObject emptyJson = new JsonObject();
        Message message = messageBuilder.buildMessage(userProfileDescriptor, emptyJson);
        assertNotNull(message);
        // Verify that default values are set correctly
    }

    @Test
    void testEmptyList() {
        JsonArray emptyArray = new JsonArray();
        Message message = messageBuilder.buildMessage(itemListDescriptor, emptyArray);
        assertNotNull(message);
        // Verify that an empty repeated field is created
    }
}
