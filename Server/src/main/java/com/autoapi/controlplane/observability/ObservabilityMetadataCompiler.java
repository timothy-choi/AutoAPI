package com.autoapi.controlplane.observability;

import com.autoapi.controlplane.configversion.CompiledObservabilityMetadataSection;
import com.autoapi.controlplane.configversion.CompiledRouteSection;
import com.autoapi.controlplane.configversion.CompiledTrafficSplitDestinationSection;
import com.autoapi.controlplane.configversion.CompiledUpstreamPoolSection;
import com.autoapi.controlplane.configversion.CompiledUpstreamTargetSection;
import com.autoapi.controlplane.configversion.HashableRuntimePayload;
import com.autoapi.controlplane.configversion.StoredRuntimeSnapshot;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ObservabilityMetadataCompiler {

  private ObservabilityMetadataCompiler() {}

  public static CompiledObservabilityMetadataSection compile(
      HashableRuntimePayload payload, long version, OffsetDateTime compiledAt) {
    List<CompiledRouteSection> routes = payload.routes();
    int routeCount = routes.size();
    Set<UUID> targetIds = new HashSet<>();
    Map<String, Integer> policyCounts = new HashMap<>();
    int rateLimit = 0;
    int retry = 0;
    int circuitBreaker = 0;
    int trafficSplit = 0;
    int backendHealth = 0;
    for (CompiledRouteSection route : routes) {
      if (route.rateLimit() != null) {
        rateLimit++;
      }
      if (route.retry() != null) {
        retry++;
      }
      if (route.circuitBreaker() != null) {
        circuitBreaker++;
      }
      if (route.trafficSplit() != null) {
        trafficSplit++;
      }
      collectTargets(route.upstreamPool(), targetIds);
      if (route.upstreamPool() != null && route.upstreamPool().backendHealth() != null) {
        backendHealth++;
      }
      if (route.trafficSplit() != null && route.trafficSplit().destinations() != null) {
        for (CompiledTrafficSplitDestinationSection destination :
            route.trafficSplit().destinations()) {
          collectTargets(destination.upstreamPool(), targetIds);
        }
      }
    }
    policyCounts.put("rate_limit", rateLimit);
    policyCounts.put("retry", retry);
    policyCounts.put("circuit_breaker", circuitBreaker);
    policyCounts.put("traffic_split", trafficSplit);
    policyCounts.put("backend_health", backendHealth);
    return new CompiledObservabilityMetadataSection(
        compiledAt.toString(), version, routeCount, targetIds.size(), Map.copyOf(policyCounts));
  }

  private static void collectTargets(CompiledUpstreamPoolSection pool, Set<UUID> targetIds) {
    if (pool == null || pool.targets() == null) {
      return;
    }
    for (CompiledUpstreamTargetSection target : pool.targets()) {
      targetIds.add(target.id());
    }
  }

  public static CompiledObservabilityMetadataSection fromSnapshot(StoredRuntimeSnapshot snapshot) {
    if (snapshot.observabilityMetadata() != null) {
      return snapshot.observabilityMetadata();
    }
    return new CompiledObservabilityMetadataSection(
        "", snapshot.version(), snapshot.routes().size(), 0, Map.of());
  }
}
