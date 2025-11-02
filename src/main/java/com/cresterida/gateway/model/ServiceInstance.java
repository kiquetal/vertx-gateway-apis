package com.cresterida.gateway.model;

import java.util.Map;

public class ServiceInstance {
    private String host;
    private int port;
    private String health;
    private Map<String, String> metadata;

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

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }
}
