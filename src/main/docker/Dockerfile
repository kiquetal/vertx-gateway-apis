# Build stage
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build

# Copy only the POM first to cache dependencies
COPY pom.xml .
RUN mvn dependency:go-offline

# Copy source and build
COPY src ./src
RUN mvn clean package -DskipTests

# Run stage
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Create non-root user for security
RUN addgroup --system --gid 1001 vertx && \
    adduser --system --uid 1001 --gid 1001 vertx

# Copy the fat JAR
COPY --from=builder /build/target/*-fat.jar app.jar

# Set permissions
RUN chown -R vertx:vertx /app

# Switch to non-root user
USER vertx

# Environment variables
ENV LOG_LEVEL=INFO
ENV SHUTDOWN_TIMEOUT_MS=30000

# Expose application port
EXPOSE 8080
EXPOSE 8082
# Set the entry point with Java 21 optimizations
ENTRYPOINT ["java", \
    "--enable-preview", \
    "--enable-native-access=ALL-UNNAMED", \
    "-XX:+UseZGC", \
    "-XX:+ZGenerational", \
    "-Djdk.virtualThreadScheduler.parallelism=16", \
    "-jar", "app.jar"]
