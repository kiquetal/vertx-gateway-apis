#!/bin/bash

# Build the Docker image locally
echo "Building Docker image..."
docker build -t vertx-gateway-local -f src/main/docker/Dockerfile .

# Run the container
echo "Starting container..."
docker run -d \
    --name vertx-gateway \
    -p 8080:8080 \
    -p 8081:8081 \
    vertx-gateway-local

# Wait for the application to start
echo "Waiting for application to start..."
sleep 5

# Test the metrics endpoint
echo "Testing metrics endpoint..."
curl -s localhost:8081/metrics | head -n 10

echo -e "\nContainer logs:"
docker logs vertx-gateway

echo -e "\nTo stop the container, run: docker stop vertx-gateway"
echo "To remove the container, run: docker rm vertx-gateway"
