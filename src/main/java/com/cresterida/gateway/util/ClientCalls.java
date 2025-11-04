package com.cresterida.gateway.util;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.Message;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.MethodDescriptor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


public class ClientCalls {
    private static final Logger LOGGER = LogManager.getLogger(ClientCalls.class);

    public static Message makeUnaryCall(
            String host,
            int port,
            String serviceName,
            String methodName,
            Message request,
            DescriptorProtos.FileDescriptorProto fileDescriptor) {
        try {
            // Create channel
            ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
                    .usePlaintext()
                    .build();

            // Get service descriptor
            Descriptors.FileDescriptor fileDesc = Descriptors.FileDescriptor.buildFrom(fileDescriptor, new Descriptors.FileDescriptor[]{});
            // Extract just the service name without the package
            String simpleServiceName = serviceName.substring(serviceName.lastIndexOf('.') + 1);
            Descriptors.ServiceDescriptor serviceDescriptor = fileDesc.findServiceByName(simpleServiceName);

            if (serviceDescriptor == null) {
                throw new IllegalArgumentException("Service not found: " + serviceName);
            }

            // Get method descriptor
            Descriptors.MethodDescriptor methodDescriptor = serviceDescriptor.findMethodByName(methodName);

            if (methodDescriptor == null) {
                throw new IllegalArgumentException("Method not found: " + methodName);
            }

            // Create gRPC method descriptor
            MethodDescriptor<Message, Message> grpcMethodDescriptor = MethodDescriptor.<Message, Message>newBuilder()
                    .setType(MethodDescriptor.MethodType.UNARY)
                    .setFullMethodName(MethodDescriptor.generateFullMethodName(serviceName, methodName))
                    .setRequestMarshaller(new DynamicMessageMarshaller(methodDescriptor.getInputType()))
                    .setResponseMarshaller(new DynamicMessageMarshaller(methodDescriptor.getOutputType()))
                    .build();

            // Make the call
            return io.grpc.stub.ClientCalls.blockingUnaryCall(channel, grpcMethodDescriptor, io.grpc.CallOptions.DEFAULT, request);

        } catch (Exception e) {
            LOGGER.error("Error making gRPC call", e);
            throw new RuntimeException("Failed to make gRPC call", e);
        }
    }
}
