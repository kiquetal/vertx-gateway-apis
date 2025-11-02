package com.cresterida.gateway.util;

import com.github.os72.protocjar.Protoc;
import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class ProtoDescriptorBuilder {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProtoDescriptorBuilder.class);

    public static class BuildResult {
        private final Descriptors.FileDescriptor fileDescriptor;
        private final Map<String, Descriptors.FileDescriptor> allDescriptors;

        BuildResult(Descriptors.FileDescriptor fileDescriptor, Map<String, Descriptors.FileDescriptor> allDescriptors) {
            this.fileDescriptor = fileDescriptor;
            this.allDescriptors = allDescriptors;
        }

        public Descriptors.FileDescriptor getFileDescriptor() {
            return fileDescriptor;
        }

        public Map<String, Descriptors.FileDescriptor> getAllDescriptors() {
            return Collections.unmodifiableMap(allDescriptors);
        }
    }

    public static BuildResult buildFromProtoDefinition(String serviceId, String protoDefinition) throws Exception {
        // Create temporary proto file
        Path tempProtoFile = Files.createTempFile(serviceId, ".proto");
        Path tempDescFile = Files.createTempFile(serviceId, ".desc");

        try {
            // Write proto definition to temp file
            Files.writeString(tempProtoFile, protoDefinition, StandardCharsets.UTF_8);

            // Prepare protoc compiler arguments
            String[] protocArgs = {
                    "-I=" + tempProtoFile.getParent().toAbsolutePath(),
                    "--include_imports",
                    "--descriptor_set_out=" + tempDescFile.toAbsolutePath(),
                    tempProtoFile.toAbsolutePath().toString()
            };

            // Run the compiler
            LOGGER.debug("Running protoc compiler...");
            int exitCode = Protoc.runProtoc(protocArgs);
            if (exitCode != 0) {
                throw new RuntimeException("protoc failed with exit code: " + exitCode);
            }

            // Read the generated descriptor file
            byte[] descBytes = Files.readAllBytes(tempDescFile);
            if (descBytes.length == 0) {
                throw new RuntimeException("protoc produced an empty descriptor file.");
            }

            // Parse the descriptor set
            DescriptorProtos.FileDescriptorSet fds = DescriptorProtos.FileDescriptorSet.parseFrom(descBytes);
            if (fds.getFileCount() == 0) {
                throw new RuntimeException("No file descriptors found in compiled proto set.");
            }

            // Build FileDescriptors, respecting dependencies (DAG sort)
            Map<String, Descriptors.FileDescriptor> builtDescriptors = new HashMap<>();
            Queue<DescriptorProtos.FileDescriptorProto> toBuild = new LinkedList<>(fds.getFileList());
            int maxAttempts = toBuild.size() * toBuild.size() + 100;
            int attempts = 0;

            while (!toBuild.isEmpty()) {
                if (attempts++ > maxAttempts) {
                    throw new RuntimeException("Failed to build file descriptors (circular dependency or missing import?)");
                }

                DescriptorProtos.FileDescriptorProto protoFile = toBuild.poll();
                List<Descriptors.FileDescriptor> depDescriptors = new ArrayList<>();
                boolean allDepsReady = true;

                for (String depName : protoFile.getDependencyList()) {
                    Descriptors.FileDescriptor dep = builtDescriptors.get(depName);
                    if (dep != null) {
                        depDescriptors.add(dep);
                    } else {
                        allDepsReady = false;
                        break;
                    }
                }

                if (allDepsReady) {
                    try {
                        Descriptors.FileDescriptor fd = Descriptors.FileDescriptor.buildFrom(
                                protoFile,
                                depDescriptors.toArray(new Descriptors.FileDescriptor[0])
                        );
                        builtDescriptors.put(protoFile.getName(), fd);
                    } catch (Descriptors.DescriptorValidationException e) {
                        throw new RuntimeException("Proto descriptor validation failed for " + protoFile.getName(), e);
                    }
                } else {
                    toBuild.add(protoFile);
                }
            }

            // The first file in the set should be our main service file
            Descriptors.FileDescriptor mainFileDescriptor = Descriptors.FileDescriptor.buildFrom(
                fds.getFile(0),
                builtDescriptors.values().toArray(new Descriptors.FileDescriptor[0])
            );

            return new BuildResult(mainFileDescriptor, builtDescriptors);

        } finally {
            // Cleanup temporary files
            Files.deleteIfExists(tempProtoFile);
            Files.deleteIfExists(tempDescFile);
        }
    }
}
