package com.autoapi.controlplane.configversion;

import com.autoapi.controlplane.persistence.RouteEntity;
import com.autoapi.controlplane.persistence.UpstreamPoolEntity;
import com.autoapi.controlplane.persistence.UpstreamTargetEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class RuntimeConfigCompiler {

  private RuntimeConfigCompiler() {}

  public static HashableRuntimePayload compile(
      UUID apiId,
      CompiledGatewaySection gateway,
      List<RouteEntity> enabledRoutes,
      Map<UUID, UpstreamPoolEntity> poolById,
      Map<UUID, List<UpstreamTargetEntity>> targetsByPool) {
    List<CompiledRouteSection> compiledRoutes = new ArrayList<>();
    List<RouteEntity> sortedRoutes = enabledRoutes.stream().sorted(routeComparator()).toList();
    for (RouteEntity route : sortedRoutes) {
      UpstreamPoolEntity pool = poolById.get(route.upstreamPoolId());
      List<UpstreamTargetEntity> poolTargets =
          targetsByPool.getOrDefault(route.upstreamPoolId(), List.of()).stream()
              .filter(UpstreamTargetEntity::enabled)
              .sorted(Comparator.comparing(UpstreamTargetEntity::id))
              .toList();
      List<CompiledUpstreamTargetSection> compiledTargets =
          poolTargets.stream()
              .map(t -> new CompiledUpstreamTargetSection(t.id(), t.url(), t.weight()))
              .toList();
      CompiledUpstreamPoolSection compiledPool =
          new CompiledUpstreamPoolSection(pool.id(), pool.loadBalancing(), compiledTargets);
      List<String> methods =
          java.util.Arrays.stream(route.methods())
              .map(m -> m.toUpperCase(Locale.ROOT))
              .sorted()
              .toList();
      compiledRoutes.add(
          new CompiledRouteSection(
              route.id(), route.host(), route.pathPrefix(), methods, compiledPool));
    }
    return new HashableRuntimePayload(apiId, gateway, compiledRoutes);
  }

  public static StoredRuntimeSnapshot toStoredSnapshot(
      HashableRuntimePayload payload, long version, String contentHash) {
    return new StoredRuntimeSnapshot(
        payload.apiId(), version, contentHash, payload.gateway(), payload.routes());
  }

  private static Comparator<RouteEntity> routeComparator() {
    return Comparator.comparing((RouteEntity r) -> r.host().toLowerCase(Locale.ROOT))
        .thenComparing((RouteEntity r) -> -normalizedPrefixLength(r.pathPrefix()))
        .thenComparing(RouteEntity::pathPrefix)
        .thenComparing(RouteEntity::id);
  }

  private static int normalizedPrefixLength(String prefix) {
    if ("/".equals(prefix)) {
      return 1;
    }
    return prefix.endsWith("/") ? prefix.length() - 1 : prefix.length();
  }
}
