#!/bin/bash

# Create monitoring directory structure
mkdir -p monitoring/{config,prometheus,grafana/{provisioning,dashboards}}

# Download JMX Prometheus Java Agent
wget https://repo1.maven.org/maven2/io/prometheus/jmx/jmx_prometheus_javaagent/0.19.0/jmx_prometheus_javaagent-0.19.0.jar \
    -O monitoring/config/jmx_prometheus_javaagent.jar

# Set proper permissions
chmod -R 777 monitoring/

echo "Setup completed. You can now run: docker-compose up -d"
echo "Access metrics at:"
echo "  - Vert.x metrics: http://localhost:8080/metrics"
echo "  - JMX metrics: http://localhost:8081/metrics"
echo "  - Prometheus: http://localhost:9090"
echo "  - Grafana: http://localhost:3000 (admin/admin)"
