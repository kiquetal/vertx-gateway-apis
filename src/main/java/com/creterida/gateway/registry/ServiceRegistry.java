package com.cresterida.gateway.registry;

import com.cresterida.gateway.model.ServiceDefinition;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ServiceRegistry {
  private final Map<String, ServiceDefinition> byId = new ConcurrentHashMap<>();
  // Map path prefix to service id for quick lookup
  private final Map<String, String> prefixToId = new ConcurrentHashMap<>();

  public synchronized ServiceDefinition add(ServiceDefinition def) {
    Objects.requireNonNull(def, "service definition");
    if (def.getId() == null || def.getId().isBlank()) {
      throw new IllegalArgumentException("service id is required");
    }
    // Ensure unique pathPrefix
    for (Map.Entry<String, String> e : prefixToId.entrySet()) {
      if (e.getKey().equals(def.getPathPrefix()) && !e.getValue().equals(def.getId())) {
        throw new IllegalArgumentException("pathPrefix already registered by another service: " + def.getPathPrefix());
      }
    }
    byId.put(def.getId(), def);
    prefixToId.put(def.getPathPrefix(), def.getId());
    return def;
  }

  public synchronized Optional<ServiceDefinition> update(String id, ServiceDefinition def) {
    if (!byId.containsKey(id)) return Optional.empty();
    def.setId(id);
    // if prefix changed ensure no conflicts
    String newPrefix = def.getPathPrefix();
    for (Map.Entry<String, String> e : prefixToId.entrySet()) {
      if (e.getKey().equals(newPrefix) && !e.getValue().equals(id)) {
        throw new IllegalArgumentException("pathPrefix already registered by another service: " + newPrefix);
      }
    }
    // remove old prefix mapping
    ServiceDefinition old = byId.get(id);
    if (old != null) {
      prefixToId.remove(old.getPathPrefix());
    }
    byId.put(id, def);
    prefixToId.put(def.getPathPrefix(), id);
    return Optional.of(def);
  }

  public synchronized Optional<ServiceDefinition> remove(String id) {
    ServiceDefinition removed = byId.remove(id);
    if (removed != null) {
      prefixToId.remove(removed.getPathPrefix());
    }
    return Optional.ofNullable(removed);
  }

  public Optional<ServiceDefinition> getById(String id) {
    return Optional.ofNullable(byId.get(id));
  }

  public List<ServiceDefinition> list() {
    return new ArrayList<>(byId.values());
  }

  public Optional<ServiceDefinition> resolveByPath(String path) {
    if (path == null) return Optional.empty();
    // longest prefix match
    String bestPrefix = null;
    for (String prefix : prefixToId.keySet()) {
      if (path.startsWith(prefix + "/") || path.equals(prefix)) {
        if (bestPrefix == null || prefix.length() > bestPrefix.length()) {
          bestPrefix = prefix;
        }
      }
    }
    if (bestPrefix == null) return Optional.empty();
    String id = prefixToId.get(bestPrefix);
    return Optional.ofNullable(byId.get(id));
  }
}
