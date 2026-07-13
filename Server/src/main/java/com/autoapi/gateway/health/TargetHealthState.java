package com.autoapi.gateway.health;

import java.time.Instant;

/** Immutable gateway-local health snapshot for one upstream target. */
public record TargetHealthState(
    int consecutiveQualifyingFailures,
    Instant ejectedUntil,
    FailureCategory lastFailureCategory,
    Instant lastUpdatedAt) {

  public static TargetHealthState healthy(Instant now) {
    return new TargetHealthState(0, null, null, now);
  }
}
