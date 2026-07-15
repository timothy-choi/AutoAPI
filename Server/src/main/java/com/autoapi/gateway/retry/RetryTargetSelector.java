package com.autoapi.gateway.retry;

import com.autoapi.config.UpstreamTargetReference;
import com.autoapi.gateway.health.HealthAwareTargetSelector;
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
      HealthAwareTargetSelector selector,
      UUID apiId,
      UUID poolId,
      List<UpstreamTargetReference> targets,
      PassiveHealthPolicy healthPolicy,
      Set<UUID> attemptedTargetIds,
      int attemptNumber) {
    List<UpstreamTargetReference> candidates = new ArrayList<>(targets);
    if (attemptNumber > 1 && attemptedTargetIds.size() < targets.size()) {
      List<UpstreamTargetReference> unattempted = new ArrayList<>();
      for (UpstreamTargetReference target : targets) {
        if (!attemptedTargetIds.contains(target.targetId())) {
          unattempted.add(target);
        }
      }
      if (!unattempted.isEmpty()) {
        candidates = unattempted;
      }
    }
    return selector.select(apiId, poolId, candidates, healthPolicy);
  }

  public static Set<UUID> newAttemptedSet() {
    return new HashSet<>();
  }
}
