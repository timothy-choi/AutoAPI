package com.autoapi.controlplane.configversion;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.UUID;

@JsonPropertyOrder({
  "policyId",
  "limitCount",
  "windowSeconds",
  "identitySource",
  "redisFailureMode"
})
public record CompiledRateLimitSection(
    UUID policyId,
    int limitCount,
    int windowSeconds,
    String identitySource,
    String redisFailureMode) {}
