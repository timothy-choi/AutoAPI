package com.autoapi.gateway.health;

import com.autoapi.config.BackendHealthPolicyConfig;
import java.time.Duration;

/** Passive outlier-detection policy compiled from runtime configuration. */
public record PassiveHealthPolicy(
    int consecutiveFailureThreshold, Duration ejectionDuration, int maxEjectionPercent) {

  public static PassiveHealthPolicy from(BackendHealthPolicyConfig config) {
    return new PassiveHealthPolicy(
        config.consecutiveFailureThreshold(),
        Duration.ofSeconds(config.ejectionDurationSeconds()),
        config.maxEjectionPercent());
  }

  public boolean ejectionEnabled() {
    return maxEjectionPercent > 0;
  }

  /**
   * Maximum concurrently ejected targets for a pool. Examples: 2 targets at 50% -> 1; 3 targets at
   * 50% -> 1; 4 targets at 50% -> 2.
   */
  public int maxEjectedTargets(int targetCount) {
    if (!ejectionEnabled() || targetCount <= 0) {
      return 0;
    }
    return (int) Math.floor(targetCount * (double) maxEjectionPercent / 100.0);
  }
}
