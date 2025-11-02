package com.cresterida.gateway.handlers;

import com.google.protobuf.DynamicMessage;
import com.google.protobuf.Descriptors;
import io.grpc.MethodDescriptor;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class DynamicMessageMarshaller implements MethodDescriptor.Marshaller<DynamicMessage> {
    private final Descriptors.Descriptor messageDescriptor;

    public DynamicMessageMarshaller(Descriptors.Descriptor messageDescriptor) {
        this.messageDescriptor = messageDescriptor;
    }

    @Override
    public DynamicMessage parse(InputStream inputStream) {
        try {
            return DynamicMessage.parseFrom(messageDescriptor, inputStream);
        } catch (IOException e) {
            throw new RuntimeException("Unable to parse dynamic message", e);
        }
    }

    @Override
    public InputStream stream(DynamicMessage abstractMessage) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            abstractMessage.writeTo(baos);
            return new ByteArrayInputStream(baos.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException("Unable to stream dynamic message", e);
        }
    }
}
