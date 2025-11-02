package com.cresterida.gateway.model;

import io.vertx.core.json.JsonObject;

public class ServiceInstance {
    private String host;
    private int port;
    private String health;

    public ServiceInstance(String host, int port) {
        this.host = host;
        this.port = port;
        this.health = "UP";
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getHealth() {
        return health;
    }

    public void setHealth(String health) {
        this.health = health;
    }

    public JsonObject toJson() {
        return new JsonObject()
            .put("host", host)
            .put("port", port)
            .put("health", health);
    }
}
