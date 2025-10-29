# Logging Architecture for Vertx Gateway APIs

This document explains how logging is implemented in both Docker and Kubernetes environments.

## Application Logging Configuration

The application uses Log4j2 for logging with the following setup:
- Logs are written to `logs/application.log`
- Log rotation is enabled for files > 10MB
- Rotated files follow the pattern: `logs/application-yyyy-MM-dd-i.log.gz`
- Keeps up to 10 rotated files

## Docker Environment Setup

In Docker, we use three main components:
1. Vertx Application (produces logs)
2. Promtail (collects logs)
3. Loki (stores logs)

### Docker Compose Configuration

```yaml
version: '3.8'

services:
  api-gateway:
    image: ghcr.io/kiquetal/vertx-gateway-apis:amd64-20251029
    volumes:
      - app_logs:/app/logs  # Named volume for logs
    ports:
      - "8080:8080"
    environment:
      - LOG_LEVEL=INFO
      - LOG_LEVEL_APP=DEBUG

  loki:
    image: grafana/loki:latest
    ports:
      - "3100:3100"
    volumes:
      - ./monitoring/loki:/etc/loki
    command: -config.file=/etc/loki/loki-config.yml

  promtail:
    image: grafana/promtail:latest
    volumes:
      - app_logs:/var/log/vertx  # Same named volume as api-gateway
      - ./monitoring/promtail:/etc/promtail
    command: -config.file=/etc/promtail/promtail-config.yml

volumes:
  app_logs:  # Define named volume for sharing logs
```

In Docker, we use named volumes to share logs between containers. This works because:
- The application container writes to `/app/logs`
- Promtail reads from `/var/log/vertx`
- Both paths are mounted to the same named volume `app_logs`

## Kubernetes Environment Setup

In Kubernetes, the architecture is different. We use the sidecar pattern where Promtail runs in the same pod as the application.

### Kubernetes Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: vertx-gateway
spec:
  selector:
    matchLabels:
      app: vertx-gateway
  template:
    metadata:
      labels:
        app: vertx-gateway
    spec:
      volumes:
        - name: app-logs
          emptyDir: {}  # Shared volume between containers in pod
        - name: promtail-config
          configMap:
            name: promtail-config
      containers:
        - name: vertx-gateway
          image: ghcr.io/kiquetal/vertx-gateway-apis:amd64-20251029
          volumeMounts:
            - name: app-logs
              mountPath: /app/logs
        - name: promtail
          image: grafana/promtail:latest
          volumeMounts:
            - name: app-logs
              mountPath: /app/logs
            - name: promtail-config
              mountPath: /etc/promtail

---
# Loki deployment is separate since it's a centralized service
apiVersion: apps/v1
kind: Deployment
metadata:
  name: loki
spec:
  selector:
    matchLabels:
      app: loki
  template:
    metadata:
      labels:
        app: loki
    spec:
      containers:
        - name: loki
          image: grafana/loki:latest
          ports:
            - containerPort: 3100
```

### Why Promtail Must Be in the Same Pod

1. **Volume Sharing**: In Kubernetes, `emptyDir` volumes are scoped to pods. Only containers within the same pod can share these volumes. This is crucial because:
   - The application writes logs to a local volume
   - Promtail needs direct file system access to read these logs
   - Running in the same pod ensures both containers can access the same files

2. **Log Lifecycle**: 
   - Logs are ephemeral and tied to the pod's lifecycle
   - When a pod restarts, both application and Promtail start fresh
   - This prevents log collection mismatches or missed entries

3. **Performance**:
   - No network overhead for log collection
   - Direct file system access is more efficient
   - Reduced latency in log collection

4. **Reliability**:
   - If the pod moves to another node, both application and Promtail move together
   - No need to handle node-to-node log shipping
   - Guaranteed log collection as long as the application is running

### Key Differences from Docker Setup

1. **Volume Type**:
   - Docker: Named volumes (persistent)
   - Kubernetes: emptyDir (ephemeral, pod-scoped)

2. **Container Coupling**:
   - Docker: Loose coupling between containers
   - Kubernetes: Tight coupling via pod

3. **Log Access**:
   - Docker: Through Docker volume driver
   - Kubernetes: Direct file system access within pod

4. **Deployment Strategy**:
   - Docker: Independent containers
   - Kubernetes: Sidecar pattern
