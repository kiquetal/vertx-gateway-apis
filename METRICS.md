# Testing Java Agent and Metrics

## Setup Instructions

1. Create the required directories:
```bash
mkdir -p monitoring/{config,prometheus,grafana/{provisioning,dashboards}}
```

2. Download the JMX Prometheus Java Agent:
```bash
wget https://repo1.maven.org/maven2/io/prometheus/jmx/jmx_prometheus_javaagent/0.19.0/jmx_prometheus_javaagent-0.19.0.jar \
    -O monitoring/config/jmx_prometheus_javaagent.jar
```

3. The docker-compose.yml includes three services:
   - api-gateway: Your Vert.x application with metrics
   - prometheus: For storing metrics
   - grafana: For visualizing metrics

## Important Volume Mounts

The docker-compose.yml includes several important volume mounts:

1. For the api-gateway service:
```yaml
volumes:
  - ./monitoring/config:/app/config
```
This mounts:
- JMX Prometheus agent configuration
- The agent JAR file
- Custom configurations

2. For Prometheus:
```yaml
volumes:
  - ./monitoring/prometheus/prometheus.yml:/etc/prometheus/prometheus.yml
  - prometheus_data:/prometheus
```
This mounts:
- Prometheus configuration
- Persistent storage for metrics

3. For Grafana:
```yaml
volumes:
  - grafana_data:/var/lib/grafana
  - ./monitoring/grafana/provisioning:/etc/grafana/provisioning
  - ./monitoring/grafana/dashboards:/var/lib/grafana/dashboards
```
This mounts:
- Persistent storage for Grafana
- Dashboards and data sources

## Testing the Setup

1. Start all services:
```bash
docker-compose up -d
```

2. Check the metrics endpoints:
```bash
# Vert.x metrics
curl http://localhost:8080/metrics

# JMX metrics
curl http://localhost:8081/metrics
```

3. Access the monitoring tools:
   - Prometheus: http://localhost:9090
   - Grafana: http://localhost:3000 (admin/admin)

4. Test with sample requests:
```bash
# Make some test requests
curl http://localhost:8080/admin/services

# Check metrics again to see the changes
curl http://localhost:8080/metrics
```

## Available Metrics

1. Vert.x Metrics (port 8080):
   - HTTP server requests
   - Event bus metrics
   - Pool metrics
   - Custom application metrics

2. JMX Metrics (port 8081):
   - JVM memory usage
   - Garbage collection stats
   - Thread counts
   - System CPU usage
   - Custom JMX metrics

## Troubleshooting

1. If metrics aren't showing up:
```bash
# Check container logs
docker-compose logs api-gateway

# Check if JMX agent is loaded
docker-compose exec api-gateway ps aux | grep java
```

2. If Prometheus can't scrape:
```bash
# Check Prometheus targets
open http://localhost:9090/targets
```

3. Volume permission issues:
```bash
# Fix permissions if needed
chmod -R 777 monitoring/
```

## Monitoring Dashboard

Access Grafana (http://localhost:3000) and create a new dashboard with these metrics:

1. Request Rate:
```promql
rate(vertx_http_server_requests_total[1m])
```

2. Response Times:
```promql
histogram_quantile(0.95, rate(vertx_http_server_response_time_seconds_bucket[5m]))
```

3. JVM Memory:
```promql
jvm_memory_heap_used_bytes
```

4. Active Connections:
```promql
vertx_http_server_connections
```
