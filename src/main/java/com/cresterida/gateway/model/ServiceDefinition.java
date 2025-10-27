package com.cresterida.gateway.model;

import io.vertx.core.json.JsonObject;

import java.net.URI;
import java.util.Objects;
import java.util.UUID;

public class ServiceDefinition {
  private String id;
  private String name;
  private String pathPrefix; // e.g. /users
  private String upstreamBaseUrl; // e.g. http://localhost:8081
  private int rateLimitPerSecond; // tokens per second
  private int burstCapacity; // bucket size
  private boolean stripPrefix = true;

  public ServiceDefinition() {
  }

  public static ServiceDefinition fromJson(JsonObject json) {
    ServiceDefinition sd = new ServiceDefinition();
    sd.id = json.getString("id", UUID.randomUUID().toString());
    sd.name = json.getString("name", sd.id);
    sd.pathPrefix = normalizePathPrefix(json.getString("pathPrefix"));
    sd.upstreamBaseUrl = normalizeBaseUrl(json.getString("upstreamBaseUrl"));
    sd.rateLimitPerSecond = Math.max(0, json.getInteger("rateLimitPerSecond", 50));
    sd.burstCapacity = Math.max(sd.rateLimitPerSecond, json.getInteger("burstCapacity", sd.rateLimitPerSecond * 2));
    sd.stripPrefix = json.getBoolean("stripPrefix", true);
    return sd;
  }

  public JsonObject toJson() {
    return new JsonObject()
      .put("id", id)
      .put("name", name)
      .put("pathPrefix", pathPrefix)
      .put("upstreamBaseUrl", upstreamBaseUrl)
      .put("rateLimitPerSecond", rateLimitPerSecond)
      .put("burstCapacity", burstCapacity)
      .put("stripPrefix", stripPrefix);
  }

  public static String normalizePathPrefix(String prefix) {
    if (prefix == null || prefix.isBlank()) throw new IllegalArgumentException("pathPrefix is required");
    String p = prefix.trim();
    if (!p.startsWith("/")) p = "/" + p;
    if (p.endsWith("/") && p.length() > 1) p = p.substring(0, p.length() - 1);
    return p;
  }

  public static String normalizeBaseUrl(String baseUrl) {
    if (baseUrl == null || baseUrl.isBlank()) throw new IllegalArgumentException("upstreamBaseUrl is required");
    String s = baseUrl.trim();
    // Validate URI
    URI uri = URI.create(s);
    if (uri.getScheme() == null || uri.getHost() == null) {
      throw new IllegalArgumentException("upstreamBaseUrl must be an absolute URL (e.g. http://host:port)");
    }
    if (s.endsWith("/")) s = s.substring(0, s.length() - 1);
    return s;
  }

  public String getId() { return id; }
  public String getName() { return name; }
  public String getPathPrefix() { return pathPrefix; }
  public String getUpstreamBaseUrl() { return upstreamBaseUrl; }
  public int getRateLimitPerSecond() { return rateLimitPerSecond; }
  public int getBurstCapacity() { return burstCapacity; }
  public boolean isStripPrefix() { return stripPrefix; }

  public void setId(String id) { this.id = id; }
  public void setName(String name) { this.name = name; }
  public void setPathPrefix(String pathPrefix) { this.pathPrefix = normalizePathPrefix(pathPrefix); }
  public void setUpstreamBaseUrl(String upstreamBaseUrl) { this.upstreamBaseUrl = normalizeBaseUrl(upstreamBaseUrl); }
  public void setRateLimitPerSecond(int rateLimitPerSecond) { this.rateLimitPerSecond = Math.max(0, rateLimitPerSecond); }
  public void setBurstCapacity(int burstCapacity) { this.burstCapacity = Math.max(this.rateLimitPerSecond, burstCapacity); }
  public void setStripPrefix(boolean stripPrefix) { this.stripPrefix = stripPrefix; }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ServiceDefinition that = (ServiceDefinition) o;
    return Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }
}
