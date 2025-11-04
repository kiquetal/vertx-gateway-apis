FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Create non-root user for security
RUN addgroup --system --gid 1001 vertx && \
    adduser --system --uid 1001 --gid 1001 vertx

# Copy the pre-built fat JAR and entrypoint script
COPY target/*-fat.jar app.jar
COPY src/main/docker/entrypoint.sh entrypoint.sh

RUN mkdir /app/logs && \
    chmod +x /app/entrypoint.sh && \
    chown vertx:vertx /app/logs

# Switch to non-root user
USER vertx

# Environment variables
ENV LOG_LEVEL=INFO
ENV LOG_LEVEL_APP=DEBUG
ENV SHUTDOWN_TIMEOUT_MS=30000

# Expose application port
EXPOSE 8080
EXPOSE 8082

ENTRYPOINT ["/app/entrypoint.sh"]
# Set the entry point with Java 21 optimizations
CMD ["java", \
    "--enable-preview", \
    "--enable-native-access=ALL-UNNAMED", \
    "-XX:+UseZGC", \
    "-XX:+ZGenerational", \
    "-Djdk.virtualThreadScheduler.parallelism=16", \
    "-jar", "/app/app.jar"]
