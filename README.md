# Vert.x API Gateway

## Table of Contents
1. [Configuration](#configuration)
   - [Environment Variables](#environment-variables)
   - [Logging Configuration](#logging-configuration)
2. [Metrics and Monitoring](#metrics-and-monitoring)
   - [HTTP Request Metrics](#http-request-metrics)
   - [Prometheus Metrics](#prometheus-metrics)
   - [JMX Monitoring](#jmx-monitoring)
   - [Available Metrics](#available-metrics)
   - [Grafana Dashboard](#grafana-dashboard)
3. [Deployment](#deployment)
   - [Docker Compose Setup](#docker-compose-setup)
4. [Local Development](#local-development)
   - [Running with JMX Monitoring](#running-with-jmx-monitoring)

## Configuration

### Environment Variables

The application can be configured using the following environment variables:

| Variable | Description | Default | Valid Values |
|----------|-------------|---------|--------------|
| LOG_LEVEL | Global logging level for the application | INFO | DEBUG, INFO, WARN, ERROR |
| LOG_LEVEL_APP | Specific logging level for application code | INFO | DEBUG, INFO, WARN, ERROR |

Example usage:
```bash
# Set more verbose logging for development
export LOG_LEVEL=DEBUG
export LOG_LEVEL_APP=DEBUG

# Set production logging levels
export LOG_LEVEL=WARN
export LOG_LEVEL_APP=INFO
```

### Logging Configuration

The application uses Log4j2 for logging. The logging configuration can be customized by modifying `src/main/resources/log4j2.xml`. The logging levels are configured in the following hierarchy:

1. `LOG_LEVEL` affects all loggers
2. `LOG_LEVEL_APP` specifically affects loggers under the `com.cresterida.gateway` package

## Metrics and Monitoring

### HTTP Request Metrics

The gateway automatically tracks HTTP request metrics using Micrometer and Prometheus. Each request is tracked with the following tags:
- `endpoint`: The request path
- `method`: The HTTP method (GET, POST, etc.)
- `status`: The HTTP status code of the response

Example metrics in Prometheus format:
```
# Total requests for GET /admin/services with 200 status
http_requests_total{endpoint="/admin/services",method="GET",status="200"} 42

# Failed requests for POST /admin/services
http_requests_total{endpoint="/admin/services",method="POST",status="400"} 3
```

These metrics allow you to:
- Monitor request volume by endpoint
- Track error rates
- Analyze traffic patterns by HTTP method
- Monitor response status codes distribution

### Prometheus Metrics

Metrics are exposed at: `http://localhost:8080/metrics`

Available metrics include:
- HTTP request counters
- Response time histograms
- Active connections
- JVM metrics
- Custom business metrics

### JMX Monitoring

JMX metrics are exposed via the Prometheus JMX exporter on port 8081.

To access JMX metrics:
```bash
# Prometheus format
curl http://localhost:8081/metrics

# Using JConsole
jconsole service:jmx:rmi:///jndi/rmi://localhost:8081/jmxrmi
```

### Available Metrics

1. Vert.x Metrics:
   - vertx_http_server_connections
   - vertx_http_server_requests_total
   - vertx_http_server_response_time_seconds
   - vertx_pool_usage
   - vertx_eventbus_messages

2. JVM Metrics:
   - jvm_memory_used_bytes
   - jvm_threads_states
   - jvm_gc_collection_seconds
   - process_cpu_usage
   - system_cpu_usage

3. Custom Gateway Metrics:
   - gateway_active_connections
   - gateway_requests_total
   - gateway_request_duration_seconds

### Grafana Dashboard

A sample Grafana dashboard is available in the `monitoring` directory. Import it into your Grafana instance to visualize the metrics.

## Deployment

### Docker Compose Setup

To run the complete monitoring stack:

```yaml
version: '3.8'
services:
  api-gateway:
    image: ghcr.io/your-repo/vertx-gateway-apis:latest
    ports:
      - "8080:8080"
      - "8081:8081"
    environment:
      - LOG_LEVEL=INFO
      - LOG_LEVEL_APP=INFO

  prometheus:
    image: prom/prometheus
    ports:
      - "9090:9090"
    volumes:
      - ./monitoring/prometheus.yml:/etc/prometheus/prometheus.yml

  grafana:
    image: grafana/grafana
    ports:
      - "3000:3000"
    depends_on:
      - prometheus
```

Save this as `docker-compose.yml` and run:
```bash
docker-compose up -d
```

Access Grafana at http://localhost:3000 (default credentials: admin/admin)

## Local Development

### Running with JMX Monitoring

To run the application locally with JMX monitoring enabled, use the following Maven command:

```bash
# With custom logging levels
LOG_LEVEL=DEBUG LOG_LEVEL_APP=DEBUG mvn exec:java \
  -Dexec.mainClass="com.cresterida.gateway.MainVerticle" \
  -Dexec.args="" \
  -Djava.agent.opts="-javaagent:monitoring/jmx/jmx_prometheus_javaagent.jar=8081:monitoring/config/jmx_prometheus_config.yaml"
```

This command will:
- Start the application using Maven
- Enable JMX monitoring on port 8081
- Use the JMX Prometheus configuration from monitoring/config/jmx_prometheus_config.yaml
- Set DEBUG level logging for development
- Expose metrics endpoint at http://localhost:8081/metrics

You can then access:
- The API Gateway at http://localhost:8080
- Metrics endpoint at http://localhost:8081/metrics
- JMX monitoring through JConsole or similar tools
