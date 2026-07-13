package com.autoapi.gateway.health;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gateway-local passive health registry. Operational state is keyed by {@link TargetKey} and
 * updated through atomic immutable state replacement.
 */
public final class TargetHealthRegistry {

  private static final Logger log = LoggerFactory.getLogger(TargetHealthRegistry.class);

  private final Clock clock;
  private final ConcurrentHashMap<TargetKey, TargetHealthState> states = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<TargetKey, TargetFingerprint> fingerprints =
      new ConcurrentHashMap<>();

  public TargetHealthRegistry(Clock clock) {
    this.clock = clock;
  }

  public TargetHealthState getState(TargetKey key) {
    TargetHealthState state = states.get(key);
    Instant now = clock.instant();
    return state != null ? state : TargetHealthState.healthy(now);
  }

  public boolean isEjected(TargetKey key) {
    return isEjected(getState(key), clock.instant());
  }

  public void recordSuccess(TargetKey key) {
    Instant now = clock.instant();
    states.compute(
        key,
        (ignored, current) -> {
          TargetHealthState previous = current != null ? current : TargetHealthState.healthy(now);
          boolean wasEjected = isEjected(previous, now);
          if (wasEjected) {
            log.info(
                "Upstream target recovered apiId={} poolId={} targetId={} previousEjectedUntil={}",
                key.apiId(),
                key.poolId(),
                key.targetId(),
                previous.ejectedUntil());
          }
          return new TargetHealthState(0, null, null, now);
        });
  }

  public void recordFailure(
      TargetKey key, FailureCategory category, PassiveHealthPolicy policy, int poolTargetCount) {
    Instant now = clock.instant();
    states.compute(
        key,
        (ignored, current) -> {
          TargetHealthState previous = current != null ? current : TargetHealthState.healthy(now);
          if (isEjected(previous, now)) {
            return new TargetHealthState(
                previous.consecutiveQualifyingFailures(), previous.ejectedUntil(), category, now);
          }

          int failures = previous.consecutiveQualifyingFailures() + 1;
          if (policy != null
              && policy.ejectionEnabled()
              && failures >= policy.consecutiveFailureThreshold()) {
            int activeEjections = countActiveEjections(key.apiId(), key.poolId(), now);
            int maxEjected = policy.maxEjectedTargets(poolTargetCount);
            if (activeEjections < maxEjected) {
              Instant ejectedUntil = now.plus(policy.ejectionDuration());
              log.warn(
                  "Ejecting upstream target apiId={} poolId={} targetId={} category={}"
                      + " ejectedUntil={} threshold={}",
                  key.apiId(),
                  key.poolId(),
                  key.targetId(),
                  category,
                  ejectedUntil,
                  policy.consecutiveFailureThreshold());
              return new TargetHealthState(0, ejectedUntil, category, now);
            }
          }
          return new TargetHealthState(failures, previous.ejectedUntil(), category, now);
        });
  }

  /**
   * Reconciles operational state when a new runtime configuration activates. Preserves health for
   * unchanged target fingerprints, resets changed URLs under the same target ID, and removes stale
   * entries.
   */
  public void reconcile(UUID apiId, Map<UUID, List<TargetFingerprint>> targetsByPool) {
    Set<TargetKey> activeKeys = new HashSet<>();
    Instant now = clock.instant();

    for (Map.Entry<UUID, List<TargetFingerprint>> poolEntry : targetsByPool.entrySet()) {
      UUID poolId = poolEntry.getKey();
      for (TargetFingerprint fingerprint : poolEntry.getValue()) {
        TargetKey key = new TargetKey(apiId, poolId, fingerprint.targetId());
        activeKeys.add(key);
        TargetFingerprint previousFingerprint = fingerprints.get(key);
        if (previousFingerprint == null || !previousFingerprint.equals(fingerprint)) {
          states.put(key, TargetHealthState.healthy(now));
        }
        fingerprints.put(key, fingerprint);
      }
    }

    states.keySet().removeIf(key -> key.apiId().equals(apiId) && !activeKeys.contains(key));
    fingerprints.keySet().removeIf(key -> key.apiId().equals(apiId) && !activeKeys.contains(key));
  }

  int countActiveEjections(UUID apiId, UUID poolId, Instant now) {
    int count = 0;
    for (Map.Entry<TargetKey, TargetHealthState> entry : states.entrySet()) {
      TargetKey key = entry.getKey();
      if (key.apiId().equals(apiId)
          && key.poolId().equals(poolId)
          && isEjected(entry.getValue(), now)) {
        count++;
      }
    }
    return count;
  }

  private static boolean isEjected(TargetHealthState state, Instant now) {
    Instant ejectedUntil = state.ejectedUntil();
    return ejectedUntil != null && ejectedUntil.isAfter(now);
  }
}
