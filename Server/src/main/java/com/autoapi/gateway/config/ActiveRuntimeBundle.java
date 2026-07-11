package com.autoapi.gateway.config;

import com.autoapi.config.RouteConfig;
import com.autoapi.config.RuntimeConfig;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class ActiveRuntimeBundle {

  private final UUID apiId;
  private final long version;
  private final String contentHash;
  private final RuntimeConfig runtimeConfig;
  private final Map<String, AtomicInteger> roundRobinCounters;

  public ActiveRuntimeBundle(
      UUID apiId, long version, String contentHash, RuntimeConfig runtimeConfig) {
    this.apiId = apiId;
    this.version = version;
    this.contentHash = contentHash;
    this.runtimeConfig = runtimeConfig;
    this.roundRobinCounters = new ConcurrentHashMap<>();
  }

  public UUID apiId() {
    return apiId;
  }

  public long version() {
    return version;
  }

  public String contentHash() {
    return contentHash;
  }

  public RuntimeConfig runtimeConfig() {
    return runtimeConfig;
  }

  public URI selectUpstream(RouteConfig route) {
    if (!route.upstream().roundRobinTargets().isEmpty()) {
      var targets = route.upstream().roundRobinTargets();
      AtomicInteger counter =
          roundRobinCounters.computeIfAbsent(route.id(), ignored -> new AtomicInteger(0));
      return targets.get(Math.floorMod(counter.getAndIncrement(), targets.size()));
    }
    return route.upstream().url();
  }
}
