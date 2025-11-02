package com.cresterida.gateway.util;

import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.Message;
import io.grpc.MethodDescriptor;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

public class DynamicMessageMarshaller implements MethodDescriptor.Marshaller<Message> {
    private final Descriptors.Descriptor messageDescriptor;

    public DynamicMessageMarshaller(Descriptors.Descriptor messageDescriptor) {
        this.messageDescriptor = messageDescriptor;
    }

    @Override
    public Message parse(InputStream inputStream) {
        try {
            return DynamicMessage.newBuilder(messageDescriptor)
                    .mergeFrom(inputStream)
                    .build();
        } catch (IOException e) {
            throw new RuntimeException("Unable to merge from input stream", e);
        }
    }

    @Override
    public InputStream stream(Message message) {
        return new ByteArrayInputStream(message.toByteArray());
    }
}

