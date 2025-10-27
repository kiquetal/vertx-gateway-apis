#!/bin/bash

# Create monitoring directories
mkdir -p monitoring/jmx
mkdir -p monitoring/prometheus
mkdir -p monitoring/grafana/dashboards
mkdir -p monitoring/grafana/provisioning/datasources

# Download JMX Exporter agent
wget https://repo1.maven.org/maven2/io/prometheus/jmx/jmx_prometheus_javaagent/0.19.0/jmx_prometheus_javaagent-0.19.0.jar \
    -O monitoring/jmx/jmx_prometheus_javaagent.jar

echo "Setup completed. JMX agent downloaded to monitoring/jmx/jmx_prometheus_javaagent.jar"
