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
### Development Mode
Run the gateway in development mode with hot reload:
```bash
mvn vertx:run
```
This will automatically reload the application when source files change.
### Production Mode
Build and run the application:
```bash
mvn clean package
java -jar target/vertx-gateway-apis-0.1.0-SNAPSHOT.jar
```
Or use Maven exec plugin:
```bash
mvn -q exec:java -Dexec.mainClass=com.creterida.gateway.Main
```
Configuration:
- Default HTTP port: `8080`
- Configure port via env `PORT` or JVM `-Dhttp.port=9090`
### Admin API (dynamic services)
Base path: `/admin/services`
- Create service
```bash
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
```bash
curl -sS http://localhost:8080/admin/services | jq
```
- Get one
```bash
curl -sS http://localhost:8080/admin/services/{id}
```
- Update
```bash
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
```bash
curl -sS -X DELETE http://localhost:8080/admin/services/{id}
```
### How routing works
- Requests are matched by the longest `pathPrefix` registered.
- If `stripPrefix=true`, the matched prefix is removed before forwarding to the upstream path.
- The first handler is a per‑service rate limiter. When exceeded, the gateway returns `429` with JSON body.
### Example
1) Register `orders` service:
```bash
curl -sS -X POST http://localhost:8080/admin/services \
  -H 'content-type: application/json' \
  -d '{
    "name": "orders",
    "pathPrefix": "/orders",
    "upstreamBaseUrl": "http://localhost:9002",
    "rateLimitPerSecond": 10,
    "burstCapacity": 20,
    "stripPrefix": true
  }'
```
2) The gateway will now proxy requests to `/orders/**` to `http://localhost:9002/**` after stripping the `/orders` prefix.
### Asynchronous Vehicle Processing
The gateway includes an asynchronous vehicle processing worker implemented using Vert.x event bus:
- Uses Vert.x event bus for non-blocking message processing
- Processes vehicle data asynchronously using Vert.x timers
- Handles vehicle information including make, model, year, and processing status
- Provides processing time tracking for each vehicle request
- Implements error handling with proper status updates
Example vehicle processing request:
```bash
curl -X POST http://localhost:8080/vehicles/process \
  -H "content-type: application/json" \
  -d '{
    "id": "v123",
    "make": "Toyota",
    "model": "Camry",
    "year": 2025
  }'
```
The worker processes each vehicle request asynchronously and returns a response with status and processing time information.
