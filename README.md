# Vert.x API Gateway

## Metrics and Monitoring

The API Gateway exposes metrics in Prometheus format and includes JMX monitoring capabilities.

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
