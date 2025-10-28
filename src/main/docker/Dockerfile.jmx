# Build stage
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build

# Copy only the POM first to cache dependencies
COPY pom.xml .
RUN mvn dependency:go-offline

# Download JMX exporter agent
RUN wget https://repo1.maven.org/maven2/io/prometheus/jmx/jmx_prometheus_javaagent/0.19.0/jmx_prometheus_javaagent-0.19.0.jar \
    -O jmx_prometheus_javaagent.jar

# Copy source and build
COPY src ./src
RUN mvn clean package -DskipTests

# Run stage
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Create non-root user for security
RUN addgroup --system --gid 1001 vertx && \
    adduser --system --uid 1001 --gid 1001 vertx

# Copy the fat JAR and monitoring components
COPY --from=builder /build/target/*-fat.jar app.jar
COPY --from=builder /build/jmx_prometheus_javaagent.jar /app/jmx_prometheus_javaagent.jar
COPY src/main/resources/jmx_prometheus_config.yaml /app/jmx_prometheus_config.yaml

# Set permissions
RUN chown -R vertx:vertx /app

# Switch to non-root user
USER vertx

# Environment variables
ENV LOG_LEVEL=INFO
ENV SHUTDOWN_TIMEOUT_MS=30000

# Expose application and metrics ports
EXPOSE 8080
EXPOSE 8081

# Set the entry point with Java 21 optimizations and JMX agent
ENTRYPOINT ["java", \
    "-javaagent:/app/jmx_prometheus_javaagent.jar=8081:/app/jmx_prometheus_config.yaml", \
    "--enable-preview", \
    "--enable-native-access=ALL-UNNAMED", \
    "-XX:+UseZGC", \
    "-XX:+ZGenerational", \
    "-Djdk.virtualThreadScheduler.parallelism=16", \
    "-jar", "app.jar"]
