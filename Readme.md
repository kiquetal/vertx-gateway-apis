# Vertx API Gateway

An API gateway built using [Vert.x](https://vertx.io/).

This project provides a minimal, high‑performance API Gateway starter focused on:
- Dynamic service registration via REST (no hardcoded services)
- Per‑service rate limiting as the first handler in the request chain
- Simple reverse proxying to upstream services

## Flow

```
     +----------------+      +------------------+      +-----------------+
     |   Client       |----->|  API Gateway     |----->|   Service       |
     +----------------+      +------------------+      +-----------------+
```

## Features

- Rate limit: Each service has its own token‑bucket rate limiter (burst + refill per second). First handler in the pipeline.
- Dynamic registry: Add/update/remove services at runtime through the admin REST API.
- Path routing: Longest‑prefix match on `pathPrefix`.
- Proxy: Forwards method, headers (minus hop‑by‑hop), query and body to the upstream base URL.

## Quick start

Requirements: JDK 17+, Maven 3.9+

Run the gateway:

```
mvn -q exec:java -Dexec.mainClass=com.example.gateway.Main
```

- Default HTTP port: `8080`
- Configure port via env `PORT` or JVM `-Dhttp.port=9090`

### Admin API (dynamic services)
Base path: `/admin/services`

- Create service

```
curl -sS -X POST http://localhost:8080/admin/services \
  -H 'content-type: application/json' \
  -d '{
    "name": "users",
    "pathPrefix": "/users",
    "upstreamBaseUrl": "http://localhost:9001",
    "rateLimitPerSecond": 20,
    "burstCapacity": 40,
    "stripPrefix": true
  }'
```

- List services

```
curl -sS http://localhost:8080/admin/services | jq
```

- Get one

```
curl -sS http://localhost:8080/admin/services/{id}
```

- Update

```
curl -sS -X PUT http://localhost:8080/admin/services/{id} \
  -H 'content-type: application/json' \
  -d '{
    "name": "users-v2",
    "pathPrefix": "/users",
    "upstreamBaseUrl": "http://localhost:9002",
    "rateLimitPerSecond": 50,
    "burstCapacity": 100,
    "stripPrefix": true
  }'
```

- Delete

```
curl -sS -X DELETE http://localhost:8080/admin/services/{id} -i
```

### How routing works
- Requests are matched by the longest `pathPrefix` registered.
- If `stripPrefix=true`, the matched prefix is removed before forwarding to the upstream path.
- The first handler is a per‑service rate limiter. When exceeded, the gateway returns `429` with JSON body.

### Example
1) Register `orders` service:
```
curl -sS -X POST http://localhost:8080/admin/services \
  -H 'content-type: application/json' \
  -d '{
    "name": "orders",
    "pathPrefix": "/orders",
    "upstreamBaseUrl": "http://localhost:7000",
    "rateLimitPerSecond": 5,
    "burstCapacity": 10,
    "stripPrefix": true
  }'
```
2) Call the service through the gateway:
```
curl -sS http://localhost:8080/orders/123
```
If more than the allowed rate hits the `/orders` routes, responses will be `429`.

## Notes / Roadmap
- Persistence for the registry (e.g., Redis) can be plugged later; current registry is in‑memory.
- Authentication and request inspection hooks can be added as optional handlers before/after rate limiting.
- Horizontal scaling will require external shared state for registration and possibly distributed rate limiting.

## Service Discovery Candidates

For the service discovery mechanism, we are considering the following technologies:

| Technology | Pros | Cons |
|---|---|---|
| **Redis** | - In-memory, resulting in very fast lookups.<br>- Simple key-value model is a natural fit for service discovery.<br>- Built-in support for Time-To-Live (TTL) on keys, allowing for automatic expiration of services that fail to send a heartbeat. | - By default, it's not persistent. While persistence can be configured, it adds complexity.<br>- Introduces another dependency to the project. |
| **SQLite** | - Embedded, no separate server process required.<br>- Simple, file-based, and provides transactional guarantees. | - Can become a bottleneck in highly concurrent environments due to file-level locking.<br>- Not ideal for distributed systems where multiple gateway instances might need to access the same service registry. |
| **SQL Database** | - Robust and provides strong transactional guarantees.<br>- Well-understood technology with a rich feature set. | - Can be overkill for a simple service discovery mechanism.<br>- Requires a separate database server, adding operational complexity. |


## Start the project with Maven

You can run the gateway directly with Maven using the Exec plugin (no manual packaging required), or build a fat JAR and run it with `java -jar`.

- Run directly (recommended for development):

```
mvn exec:java -Dexec.mainClass=com.example.gateway.Main
```

- Change the HTTP port (two options):

```
# JVM system property
mvn -Dhttp.port=9090 exec:java

# Environment variable
# Linux / macOS
PORT=9090 mvn exec:java
# Windows PowerShell
$env:PORT=9090; mvn exec:java
# Windows CMD
set PORT=9090 && mvn exec:java
```

- Clean build then run in one go:

```
mvn clean compile exec:java -Dexec.mainClass=com.example.gateway.Main
```

- Build a fat JAR and run (good for staging/production tests):

```
# Build shaded (fat) JAR
mvn -DskipTests package

# Run the shaded JAR (version may change; adjust the filename if needed)
java -jar target/vertx-gateway-apis-0.1.0-SNAPSHOT-shaded.jar

# Override port when running the JAR
java -Dhttp.port=9090 -jar target/vertx-gateway-apis-0.1.0-SNAPSHOT-shaded.jar
```

To stop the gateway in the foreground, press Ctrl+C.
