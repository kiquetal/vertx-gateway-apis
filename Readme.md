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