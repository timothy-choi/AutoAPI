package com.autoapi.gateway.health;

import com.autoapi.config.UpstreamConfig;
import com.autoapi.config.UpstreamTargetReference;
import com.autoapi.gateway.GatewayProperties;
import com.autoapi.gateway.config.ActiveRuntimeBundle;
import com.autoapi.gateway.config.ActiveRuntimeConfigHolder;
import com.autoapi.runtime.AutoApiRole;
import com.autoapi.runtime.ConditionalOnAutoApiRole;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * Internal gateway-local upstream health visibility. Unauthenticated in the MVP; must not be
 * exposed publicly.
 */
@Component
@ConditionalOnAutoApiRole({AutoApiRole.GATEWAY, AutoApiRole.COMBINED})
public class GatewayInternalHealthHandler {

  private final ActiveRuntimeConfigHolder activeRuntimeConfigHolder;
  private final TargetHealthRegistry registry;
  private final GatewayProperties gatewayProperties;
  private final Clock clock;

  public GatewayInternalHealthHandler(
      ActiveRuntimeConfigHolder activeRuntimeConfigHolder,
      TargetHealthRegistry registry,
      GatewayProperties gatewayProperties,
      Clock clock) {
    this.activeRuntimeConfigHolder = activeRuntimeConfigHolder;
    this.registry = registry;
    this.gatewayProperties = gatewayProperties;
    this.clock = clock;
  }

  public Mono<ServerResponse> upstreamHealth(ServerRequest request) {
    ActiveRuntimeBundle bundle = activeRuntimeConfigHolder.getActiveForRequest();
    if (bundle == null) {
      return ServerResponse.status(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(Map.of("error", Map.of("code", "GATEWAY_NOT_READY")));
    }

    Instant now = clock.instant();
    Map<UUID, PoolAccumulator> pools = new LinkedHashMap<>();
    bundle
        .runtimeConfig()
        .routes()
        .forEach(
            route -> {
              UpstreamConfig upstream = route.upstream();
              if (upstream.poolId() == null || upstream.targets().isEmpty()) {
                return;
              }
              PoolAccumulator pool =
                  pools.computeIfAbsent(
                      upstream.poolId(),
                      ignored -> new PoolAccumulator(bundle.apiId(), upstream.poolId()));
              for (UpstreamTargetReference target : upstream.targets()) {
                if (pool.targetIds.add(target.targetId())) {
                  TargetKey key =
                      new TargetKey(bundle.apiId(), upstream.poolId(), target.targetId());
                  TargetHealthState state = registry.getState(key);
                  pool.targets.add(toTargetView(target, state, now));
                }
              }
            });

    List<Map<String, Object>> poolViews = new ArrayList<>();
    for (PoolAccumulator pool : pools.values()) {
      poolViews.add(
          Map.of(
              "apiId", pool.apiId.toString(),
              "poolId", pool.poolId.toString(),
              "targets", pool.targets));
    }

    return ServerResponse.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            Map.of(
                "gatewayId",
                gatewayProperties.gatewayId() == null ? "unknown" : gatewayProperties.gatewayId(),
                "pools",
                poolViews));
  }

  private static Map<String, Object> toTargetView(
      UpstreamTargetReference target, TargetHealthState state, Instant now) {
    boolean ejected = state.ejectedUntil() != null && state.ejectedUntil().isAfter(now);
    Map<String, Object> view = new LinkedHashMap<>();
    view.put("targetId", target.targetId().toString());
    view.put("url", target.url().getScheme() + "://" + target.url().getAuthority());
    view.put("state", ejected ? "EJECTED" : "HEALTHY");
    view.put("consecutiveFailures", state.consecutiveQualifyingFailures());
    view.put("ejectedUntil", state.ejectedUntil() == null ? null : state.ejectedUntil().toString());
    view.put(
        "lastFailureCategory",
        state.lastFailureCategory() == null ? null : state.lastFailureCategory().name());
    return view;
  }

  private static final class PoolAccumulator {
    private final UUID apiId;
    private final UUID poolId;
    private final java.util.Set<UUID> targetIds = new java.util.LinkedHashSet<>();
    private final List<Map<String, Object>> targets = new ArrayList<>();

    private PoolAccumulator(UUID apiId, UUID poolId) {
      this.apiId = apiId;
      this.poolId = poolId;
    }
  }
}
