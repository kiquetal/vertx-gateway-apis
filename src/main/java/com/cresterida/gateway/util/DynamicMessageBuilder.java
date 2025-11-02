package com.cresterida.gateway.util;

import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class DynamicMessageBuilder {

    public Message buildMessage(Descriptors.Descriptor descriptor, Object jsonInput) {
        if (jsonInput instanceof JsonArray && isRepeatedRootMessage(descriptor)) {
            return handleRepeatedRootMessage(descriptor, (JsonArray) jsonInput);
        } else if (jsonInput instanceof JsonObject) {
            return buildMessageFromJson(descriptor, (JsonObject) jsonInput);
        } else {
            throw new IllegalArgumentException("Unsupported input type: " + jsonInput.getClass());
        }
    }

    private boolean isRepeatedRootMessage(Descriptors.Descriptor descriptor) {
        // Check if this is a wrapper message with a single repeated field
        return descriptor.getFields().size() == 1 &&
               descriptor.getFields().get(0).isRepeated();
    }

    private Message handleRepeatedRootMessage(Descriptors.Descriptor descriptor, JsonArray jsonArray) {
        DynamicMessage.Builder builder = DynamicMessage.newBuilder(descriptor);
        Descriptors.FieldDescriptor repeatedField = descriptor.getFields().get(0);

        for (Object item : jsonArray) {
            if (item instanceof JsonObject) {
                DynamicMessage nestedMessage = (DynamicMessage) buildMessage(
                    repeatedField.getMessageType(),
                    item
                );
                builder.addRepeatedField(repeatedField, nestedMessage);
            } else {
                builder.addRepeatedField(repeatedField, convertJsonValueToProtoValue(item, repeatedField));
            }
        }

        return builder.build();
    }

    private Message buildMessageFromJson(Descriptors.Descriptor descriptor, JsonObject jsonInput) {
        DynamicMessage.Builder builder = DynamicMessage.newBuilder(descriptor);

        for (Descriptors.FieldDescriptor field : descriptor.getFields()) {
            if (!jsonInput.containsKey(field.getName())) {
                continue;
            }

            if (field.isRepeated()) {
                handleRepeatedField(builder, field, jsonInput.getJsonArray(field.getName()));
            } else {
                handleSingleField(builder, field, jsonInput);
            }
        }

        return builder.build();
    }

    private void handleRepeatedField(DynamicMessage.Builder builder,
                                   Descriptors.FieldDescriptor field,
                                   JsonArray jsonArray) {
        if (jsonArray == null) return;

        for (Object element : jsonArray) {
            if (field.getType() == Descriptors.FieldDescriptor.Type.MESSAGE) {
                if (element instanceof JsonObject) {
                    Message nestedMessage = buildMessage(field.getMessageType(), element);
                    builder.addRepeatedField(field, nestedMessage);
                }
            } else {
                builder.addRepeatedField(field, convertJsonValueToProtoValue(element, field));
            }
        }
    }

    private void handleSingleField(DynamicMessage.Builder builder,
                                 Descriptors.FieldDescriptor field,
                                 JsonObject jsonInput) {
        Object value = jsonInput.getValue(field.getName());

        if (field.getType() == Descriptors.FieldDescriptor.Type.MESSAGE) {
            if (value instanceof JsonObject) {
                Message nestedMessage = buildMessage(field.getMessageType(), value);
                builder.setField(field, nestedMessage);
            }
        } else {
            builder.setField(field, convertJsonValueToProtoValue(value, field));
        }
    }

    private Object convertJsonValueToProtoValue(Object value, Descriptors.FieldDescriptor field) {
        if (value == null) {
            return field.getDefaultValue();
        }

        switch (field.getType()) {
            case INT32:
            case SINT32:
            case FIXED32:
                return ((Number) value).intValue();
            case INT64:
            case SINT64:
            case FIXED64:
                return ((Number) value).longValue();
            case DOUBLE:
                return ((Number) value).doubleValue();
            case FLOAT:
                return ((Number) value).floatValue();
            case BOOL:
                return (Boolean) value;
            case STRING:
                return String.valueOf(value);
            case ENUM:
                if (value instanceof String) {
                    return field.getEnumType().findValueByName((String) value);
                } else if (value instanceof Number) {
                    return field.getEnumType().findValueByNumber(((Number) value).intValue());
                }
                return field.getDefaultValue();
            default:
                return value;
        }
    }
}
