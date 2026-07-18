package com.autoapi.config;

import java.util.UUID;

public record RuntimeCircuitBreakerPolicyConfig(
    UUID policyId,
    int failureThreshold,
    int rollingWindowSeconds,
    int openDurationSeconds,
    int halfOpenMaxRequests,
    int successThreshold,
    RuntimeCircuitBreakerFailurePredicate failurePredicate) {

  public boolean circuitBreakerEnabled() {
    return policyId != null;
  }
}
