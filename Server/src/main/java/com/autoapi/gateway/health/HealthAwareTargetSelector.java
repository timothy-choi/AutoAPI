package com.autoapi.gateway.health;

import com.autoapi.config.UpstreamTargetReference;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Health-aware round-robin selector. Skips ejected targets when a passive-health policy is present;
 * when all targets are ejected, falls back to the target with the earliest ejection expiry.
 */
public final class HealthAwareTargetSelector {

  private static final Logger log = LoggerFactory.getLogger(HealthAwareTargetSelector.class);

  private final TargetHealthRegistry registry;
  private final Clock clock;
  private final ConcurrentHashMap<String, AtomicLong> roundRobinCounters =
      new ConcurrentHashMap<>();

  public HealthAwareTargetSelector(TargetHealthRegistry registry, Clock clock) {
    this.registry = registry;
    this.clock = clock;
  }

  public SelectedTarget select(
      UUID apiId, UUID poolId, List<UpstreamTargetReference> targets, PassiveHealthPolicy policy) {
    if (targets == null || targets.isEmpty()) {
      throw new IllegalArgumentException("Target list must not be empty");
    }
    if (policy == null || !policy.ejectionEnabled()) {
      return new SelectedTarget(selectRoundRobin(apiId, poolId, targets), false);
    }

    Instant now = clock.instant();
    List<UpstreamTargetReference> eligible = new ArrayList<>();
    for (UpstreamTargetReference target : targets) {
      TargetKey key = new TargetKey(apiId, poolId, target.targetId());
      if (!isCurrentlyEjected(key, now)) {
        eligible.add(target);
      }
    }

    if (!eligible.isEmpty()) {
      return new SelectedTarget(selectRoundRobin(apiId, poolId, eligible), false);
    }

    UpstreamTargetReference forced =
        targets.stream()
            .min(
                Comparator.comparing(
                    target ->
                        registry
                            .getState(new TargetKey(apiId, poolId, target.targetId()))
                            .ejectedUntil(),
                    Comparator.nullsLast(Comparator.naturalOrder())))
            .orElseThrow();
    log.warn(
        "All upstream targets ejected; forcing degraded selection apiId={} poolId={} targetId={}",
        apiId,
        poolId,
        forced.targetId());
    return new SelectedTarget(forced, true);
  }

  private UpstreamTargetReference selectRoundRobin(
      UUID apiId, UUID poolId, List<UpstreamTargetReference> targets) {
    String counterKey = apiId + ":" + poolId;
    AtomicLong counter =
        roundRobinCounters.computeIfAbsent(counterKey, ignored -> new AtomicLong(0));
    int index = Math.floorMod(counter.getAndIncrement(), targets.size());
    return targets.get(index);
  }

  private boolean isCurrentlyEjected(TargetKey key, Instant now) {
    TargetHealthState state = registry.getState(key);
    Instant ejectedUntil = state.ejectedUntil();
    return ejectedUntil != null && ejectedUntil.isAfter(now);
  }
}
