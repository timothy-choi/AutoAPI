package com.autoapi.controlplane.configversion;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.UUID;

@JsonPropertyOrder({
  "policyId",
  "failureThreshold",
  "rollingWindowSeconds",
  "openDurationSeconds",
  "halfOpenMaxRequests",
  "successThreshold",
  "failurePredicate"
})
public record CompiledCircuitBreakerSection(
    UUID policyId,
    int failureThreshold,
    int rollingWindowSeconds,
    int openDurationSeconds,
    int halfOpenMaxRequests,
    int successThreshold,
    CompiledCircuitBreakerFailurePredicateSection failurePredicate) {}
