package com.autoapi.gateway.circuitbreaker;

import com.autoapi.config.RuntimeCircuitBreakerPolicyConfig;
import com.autoapi.config.UpstreamTargetReference;
import com.autoapi.gateway.health.HealthAwareTargetSelector;
import com.autoapi.gateway.health.PassiveHealthPolicy;
import com.autoapi.gateway.health.SelectedTarget;
import com.autoapi.gateway.health.TargetKey;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Selects upstream targets after passive health and circuit breaker admission checks. */
public final class GatewayTargetSelector {

  private final HealthAwareTargetSelector healthSelector;
  private final CircuitBreakerRegistry circuitBreakerRegistry;

  public GatewayTargetSelector(
      HealthAwareTargetSelector healthSelector, CircuitBreakerRegistry circuitBreakerRegistry) {
    this.healthSelector = healthSelector;
    this.circuitBreakerRegistry = circuitBreakerRegistry;
  }

  public SelectedTarget select(
      UUID apiId,
      UUID poolId,
      List<UpstreamTargetReference> targets,
      PassiveHealthPolicy healthPolicy,
      RuntimeCircuitBreakerPolicyConfig circuitPolicy,
      String routeId) {
    return select(apiId, poolId, targets, healthPolicy, circuitPolicy, routeId, Set.of());
  }

  public SelectedTarget select(
      UUID apiId,
      UUID poolId,
      List<UpstreamTargetReference> targets,
      PassiveHealthPolicy healthPolicy,
      RuntimeCircuitBreakerPolicyConfig circuitPolicy,
      String routeId,
      Set<UUID> excludedTargetIds) {
    if (targets == null || targets.isEmpty()) {
      throw new IllegalArgumentException("Target list must not be empty");
    }
    if (circuitPolicy == null || !circuitPolicy.circuitBreakerEnabled()) {
      return selectWithExclusions(apiId, poolId, targets, healthPolicy, excludedTargetIds);
    }

    List<UpstreamTargetReference> candidates = applyExclusions(targets, excludedTargetIds);
    Set<UUID> rejectedByCircuit = new HashSet<>();
    for (int attempt = 0; attempt < candidates.size(); attempt++) {
      List<UpstreamTargetReference> remaining = new ArrayList<>();
      for (UpstreamTargetReference target : candidates) {
        if (!rejectedByCircuit.contains(target.targetId())) {
          remaining.add(target);
        }
      }
      if (remaining.isEmpty()) {
        break;
      }
      SelectedTarget selected = healthSelector.select(apiId, poolId, remaining, healthPolicy);
      TargetKey targetKey = new TargetKey(apiId, poolId, selected.target().targetId());
      CircuitAdmission admission =
          circuitBreakerRegistry.tryAdmit(targetKey, circuitPolicy, apiId, routeId);
      if (admission == CircuitAdmission.ALLOW) {
        return selected;
      }
      rejectedByCircuit.add(selected.target().targetId());
    }
    throw new CircuitBreakerOpenException("All upstream targets have open circuit breakers");
  }

  public boolean hasCircuitEligibleTarget(
      UUID apiId,
      UUID poolId,
      List<UpstreamTargetReference> targets,
      PassiveHealthPolicy healthPolicy,
      RuntimeCircuitBreakerPolicyConfig circuitPolicy,
      String routeId) {
    if (circuitPolicy == null || !circuitPolicy.circuitBreakerEnabled()) {
      try {
        SelectedTarget selected = healthSelector.select(apiId, poolId, targets, healthPolicy);
        return selected != null && selected.target() != null && !selected.forcedSelection();
      } catch (IllegalArgumentException ex) {
        return false;
      }
    }
    try {
      select(apiId, poolId, targets, healthPolicy, circuitPolicy, routeId);
      return true;
    } catch (CircuitBreakerOpenException ex) {
      return false;
    } catch (IllegalArgumentException ex) {
      return false;
    }
  }

  private SelectedTarget selectWithExclusions(
      UUID apiId,
      UUID poolId,
      List<UpstreamTargetReference> targets,
      PassiveHealthPolicy healthPolicy,
      Set<UUID> excludedTargetIds) {
    List<UpstreamTargetReference> candidates = applyExclusions(targets, excludedTargetIds);
    return healthSelector.select(apiId, poolId, candidates, healthPolicy);
  }

  private static List<UpstreamTargetReference> applyExclusions(
      List<UpstreamTargetReference> targets, Set<UUID> excludedTargetIds) {
    if (excludedTargetIds == null || excludedTargetIds.isEmpty()) {
      return targets;
    }
    List<UpstreamTargetReference> remaining = new ArrayList<>();
    for (UpstreamTargetReference target : targets) {
      if (!excludedTargetIds.contains(target.targetId())) {
        remaining.add(target);
      }
    }
    return remaining.isEmpty() ? targets : remaining;
  }
}
