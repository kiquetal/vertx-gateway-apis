package com.cresterida.gateway.model;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;
import java.net.URI;

public class ServiceDefinition {
    private final String id;
    private final String name;
    private final String packageName;
    private final String version;
    private final String protoDefinition;
    private final List<ServiceInstance> instances;
    private final Map<String, EndpointDefinition> endpoints;
    private final int burstCapacity;
    private final int rateLimitPerSecond;
    private final Map<String, JsonFieldMapping> fieldMappings;
    private final String pathPrefix;
    private final String upstreamBaseUrl;
    private final boolean stripPrefix;

    private ServiceDefinition(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.packageName = builder.packageName;
        this.version = builder.version;
        this.protoDefinition = builder.protoDefinition;
        this.instances = new ArrayList<>(builder.instances);
        this.endpoints = new HashMap<>(builder.endpoints);
        this.burstCapacity = builder.burstCapacity;
        this.rateLimitPerSecond = builder.rateLimitPerSecond;
        this.fieldMappings = new HashMap<>(builder.fieldMappings);
        this.pathPrefix = builder.pathPrefix;
        this.upstreamBaseUrl = builder.upstreamBaseUrl;
        this.stripPrefix = builder.stripPrefix;
    }

    public static ServiceDefinition fromJson(JsonObject json) {
        if (json == null) {
            throw new IllegalArgumentException("JSON cannot be null");
        }

        Builder builder = new Builder()
            .setId(json.getString("id"))
            .setName(json.getString("name"))
            .setPackage(json.getString("packageName"))
            .setVersion(json.getString("version"))
            .setProtoDefinition(json.getString("protoDefinition"))
            .setBurstCapacity(json.getInteger("burstCapacity", 100))
            .setRateLimitPerSecond(json.getInteger("rateLimitPerSecond", 10))
            .setPathPrefix(json.getString("pathPrefix", "/"))
            .setUpstreamBaseUrl(json.getString("upstreamBaseUrl"))
            .setStripPrefix(json.getBoolean("stripPrefix", false));

        if (json.containsKey("instances")) {
            JsonArray instances = json.getJsonArray("instances");
            if (instances != null) {
                instances.forEach(inst -> {
                    if (inst instanceof JsonObject) {
                        JsonObject instObj = (JsonObject) inst;
                        builder.addInstance(new ServiceInstance(
                            instObj.getString("host"),
                            instObj.getInteger("port")
                        ));
                    }
                });
            }
        }

        // Parse endpoints if provided and add to builder
        if (json.containsKey("endpoints")) {
            JsonArray eps = json.getJsonArray("endpoints");
            if (eps != null) {
                eps.forEach(e -> {
                    if (e instanceof JsonObject) {
                        EndpointDefinition ed = EndpointDefinition.fromJson((JsonObject) e);
                        if (ed.getName() != null) {
                            builder.addEndpoint(ed);
                        }
                    }
                });
            }
        }

        return builder.build();
    }

    // Getters with proper return types and immutable collections
    public String getId() { return id; }
    public String getName() { return name; }
    public String getPackageName() { return packageName; }
    public String getVersion() { return version; }
    public String getProtoDefinition() { return protoDefinition; }
    public List<ServiceInstance> getInstances() { return Collections.unmodifiableList(instances); }
    public Map<String, EndpointDefinition> getEndpoints() { return Collections.unmodifiableMap(endpoints); }
    public int getBurstCapacity() { return burstCapacity; }
    public int getRateLimitPerSecond() { return rateLimitPerSecond; }
    public String getPathPrefix() { return pathPrefix; }
    public String getUpstreamBaseUrl() { return upstreamBaseUrl; }
    public boolean isStripPrefix() { return stripPrefix; }

    // Returns an active instance from the list of instances, or null if no instances are available
    public ServiceInstance getActiveInstance() {
        if (instances == null || instances.isEmpty()) {
            return null;
        }
        // For now, simply return the first instance
        // TODO: Implement more sophisticated instance selection based on health checks or load balancing
        return instances.get(0);
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject()
            .put("id", id)
            .put("name", name)
            .put("packageName", packageName)
            .put("version", version)
            .put("protoDefinition", protoDefinition)
            .put("burstCapacity", burstCapacity)
            .put("rateLimitPerSecond", rateLimitPerSecond)
            .put("pathPrefix", pathPrefix)
            .put("upstreamBaseUrl", upstreamBaseUrl)
            .put("stripPrefix", stripPrefix);

        if (!instances.isEmpty()) {
            JsonArray instancesArray = new JsonArray();
            instances.forEach(instance -> {
                instancesArray.add(instance.toJson());
            });
            json.put("instances", instancesArray);
        }

        // Serialize endpoints if present
        if (!endpoints.isEmpty()) {
            JsonArray eps = new JsonArray();
            endpoints.values().forEach(ed -> eps.add(ed.toJson()));
            json.put("endpoints", eps);
        }

        return json;
    }

    public static class Builder {
        private String id;
        private String name;
        private String packageName;
        private String version;
        private String protoDefinition;
        private List<ServiceInstance> instances = new ArrayList<>();
        private Map<String, EndpointDefinition> endpoints = new HashMap<>();
        private int burstCapacity = 100;
        private int rateLimitPerSecond = 10;
        private Map<String, JsonFieldMapping> fieldMappings = new HashMap<>();
        private String pathPrefix = "/";
        private String upstreamBaseUrl;
        private boolean stripPrefix;

        public Builder setId(String id) { this.id = id; return this; }
        public Builder setName(String name) { this.name = name; return this; }
        public Builder setPackage(String packageName) { this.packageName = packageName; return this; }
        public Builder setVersion(String version) { this.version = version; return this; }
        public Builder setProtoDefinition(String protoDefinition) { this.protoDefinition = protoDefinition; return this; }
        public Builder addInstance(ServiceInstance instance) { this.instances.add(instance); return this; }
        public Builder addEndpoint(EndpointDefinition endpoint) { this.endpoints.put(endpoint.getName(), endpoint); return this; }
        public Builder setBurstCapacity(int burstCapacity) { this.burstCapacity = burstCapacity; return this; }
        public Builder setRateLimitPerSecond(int rateLimitPerSecond) { this.rateLimitPerSecond = rateLimitPerSecond; return this; }
        public Builder setPathPrefix(String pathPrefix) { this.pathPrefix = pathPrefix; return this; }
        public Builder setUpstreamBaseUrl(String upstreamBaseUrl) { this.upstreamBaseUrl = upstreamBaseUrl; return this; }
        public Builder setStripPrefix(boolean stripPrefix) { this.stripPrefix = stripPrefix; return this; }

        public ServiceDefinition build() {
            return new ServiceDefinition(this);
        }
    }
}
