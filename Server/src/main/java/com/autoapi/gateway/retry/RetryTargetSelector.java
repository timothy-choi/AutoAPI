package com.autoapi.gateway.retry;

import com.autoapi.config.RuntimeCircuitBreakerPolicyConfig;
import com.autoapi.config.UpstreamTargetReference;
import com.autoapi.gateway.circuitbreaker.GatewayTargetSelector;
import com.autoapi.gateway.health.PassiveHealthPolicy;
import com.autoapi.gateway.health.SelectedTarget;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Selects upstream targets across retry attempts, preferring targets not yet attempted. */
public final class RetryTargetSelector {

  private RetryTargetSelector() {}

  public static SelectedTarget selectForAttempt(
      GatewayTargetSelector selector,
      UUID apiId,
      UUID poolId,
      List<UpstreamTargetReference> targets,
      PassiveHealthPolicy healthPolicy,
      RuntimeCircuitBreakerPolicyConfig circuitPolicy,
      String routeId,
      Set<UUID> attemptedTargetIds,
      int attemptNumber) {
    List<UpstreamTargetReference> candidates = new ArrayList<>(targets);
    Set<UUID> excluded = new HashSet<>();
    if (attemptNumber > 1 && attemptedTargetIds.size() < targets.size()) {
      for (UpstreamTargetReference target : targets) {
        if (attemptedTargetIds.contains(target.targetId())) {
          excluded.add(target.targetId());
        }
      }
    }
    return selector.select(
        apiId, poolId, candidates, healthPolicy, circuitPolicy, routeId, excluded);
  }

  public static Set<UUID> newAttemptedSet() {
    return new HashSet<>();
  }
}
