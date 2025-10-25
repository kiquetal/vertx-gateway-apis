# Vertx API Gateway

An API gateway.

## Flow

```
     +----------------+      +------------------+      +-----------------+
     |   Client       |----->|  API Gateway     |----->|   Service       |
     +----------------+      +------------------+      +-----------------+
```

## Features

*   **Rate Limit:** Protects services from being overwhelmed with requests.
*   **Authentication:** Secures services by verifying the identity of users or applications.
*   **Service Registration:** Allows services to be dynamically discovered and managed.
*   **Request Inspection:** Allows for inspection of incoming requests before forwarding them to the target service.
