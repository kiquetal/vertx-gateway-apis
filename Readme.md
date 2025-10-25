# Vertx API Gateway

An API gateway built using [Vert.x](https://vertx.io/).

This project aims to provide a simple, yet powerful and flexible API gateway. The first step is to implement a service discovery mechanism, so services can be added dynamically.

## Flow

```
     +----------------+      +------------------+      +-----------------+
     |   Client       |----->|  API Gateway     |----->|   Service       |
     +----------------+      +------------------+      +-----------------+
```

## Features

*   **Rate Limit:** All services will have a rate limit out of the box. This protects services from being overwhelmed with requests.
*   **Authentication:** Services can opt to have authentication or not.
*   **Service Registration:** Allows services to be dynamically discovered and managed.
*   **Request Inspection:** Allows for inspection of incoming requests before forwarding them to the target service.

## Service Discovery Candidates

For the service discovery mechanism, we are considering the following technologies:

| Technology | Pros | Cons |
|---|---|---|
| **Redis** | - In-memory, resulting in very fast lookups.<br>- Simple key-value model is a natural fit for service discovery.<br>- Built-in support for Time-To-Live (TTL) on keys, allowing for automatic expiration of services that fail to send a heartbeat. | - By default, it's not persistent. While persistence can be configured, it adds complexity.<br>- Introduces another dependency to the project. |
| **SQLite** | - Embedded, no separate server process required.<br>- Simple, file-based, and provides transactional guarantees. | - Can become a bottleneck in highly concurrent environments due to file-level locking.<br>- Not ideal for distributed systems where multiple gateway instances might need to access the same service registry. |
| **SQL Database** | - Robust and provides strong transactional guarantees.<br>- Well-understood technology with a rich feature set. | - Can be overkill for a simple service discovery mechanism.<br>- Requires a separate database server, adding operational complexity. |