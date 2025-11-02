package com.cresterida.gateway.model;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class ServiceDefinition {
    private String serviceId;
    private String name;
    private String packageName;
    private String version;
    private String protoDefinition;
    private List<ServiceInstance> instances;
    private Map<String, EndpointDefinition> endpoints;

    public ServiceDefinition() {
        this.instances = new CopyOnWriteArrayList<>();
    }

    // Gets an active (healthy) instance using a simple round-robin approach
    public ServiceInstance getActiveInstance() {
        return instances.stream()
            .filter(instance -> "UP".equals(instance.getHealth()))
            .findFirst()
            .orElse(null);
    }

    public String getServiceId() {
        return serviceId;
    }

    public void setServiceId(String serviceId) {
        this.serviceId = serviceId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPackage() {
        return packageName;
    }

    public void setPackage(String packageName) {
        this.packageName = packageName;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getProtoDefinition() {
        return protoDefinition;
    }

    public void setProtoDefinition(String protoDefinition) {
        this.protoDefinition = protoDefinition;
    }

    public List<ServiceInstance> getInstances() {
        return instances;
    }

    public void setInstances(List<ServiceInstance> instances) {
        this.instances = new CopyOnWriteArrayList<>(instances);
    }

    public void addInstance(ServiceInstance instance) {
        this.instances.add(instance);
    }

    public Map<String, EndpointDefinition> getEndpoints() {
        return endpoints;
    }

    public void setEndpoints(Map<String, EndpointDefinition> endpoints) {
        this.endpoints = endpoints;
    }

    public EndpointDefinition getEndpoint(String name) {
        return endpoints.get(name);
    }
}
