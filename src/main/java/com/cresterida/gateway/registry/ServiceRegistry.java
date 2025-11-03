package com.cresterida.gateway.registry;

import com.cresterida.gateway.model.ServiceDefinition;
import com.cresterida.gateway.model.ServiceType;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ServiceRegistry {
    private final Map<String, ServiceDefinition> services = new ConcurrentHashMap<>();

    public void add(ServiceDefinition service) {
        services.put(service.getId(), service);
    }

    public Optional<ServiceDefinition> getById(String id) {
        return Optional.ofNullable(services.get(id));
    }

    public List<ServiceDefinition> list() {
        return List.copyOf(services.values());
    }

    public Optional<ServiceDefinition> update(String id, ServiceDefinition service) {
        if (services.containsKey(id)) {
            services.put(id, service);
            return Optional.of(service);
        }
        return Optional.empty();
    }

    public Optional<ServiceDefinition> remove(String id) {
        return Optional.ofNullable(services.remove(id));
    }

    public List<ServiceDefinition> listByType(ServiceType type) {
        return services.values().stream()
            .filter(service -> service.getType() == type)
            .collect(Collectors.toList());
    }

    public Optional<ServiceDefinition> resolveByPath(String path, ServiceType type) {
        return services.values().stream()
            .filter(service -> service.getType() == type && path.startsWith(service.getPathPrefix()))
            .findFirst();
    }

    public Optional<ServiceDefinition> resolveByPath(String path) {
        return services.values().stream()
            .filter(service -> path.startsWith(service.getPathPrefix()))
            .findFirst();
    }
}
