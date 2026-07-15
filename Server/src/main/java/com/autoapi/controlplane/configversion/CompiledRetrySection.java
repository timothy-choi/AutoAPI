package com.autoapi.controlplane.configversion;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.List;
import java.util.UUID;

@JsonPropertyOrder({
  "policyId",
  "maxAttempts",
  "perAttemptTimeoutMs",
  "retryOnConnectFailure",
  "retryOnConnectionReset",
  "retryOnDnsFailure",
  "retryOnResponseTimeout",
  "retryableMethods",
  "requireIdempotencyKeyForUnsafeMethods",
  "budgetPercent",
  "budgetMinRetriesPerSecond",
  "budgetWindowSeconds"
})
public record CompiledRetrySection(
    UUID policyId,
    int maxAttempts,
    int perAttemptTimeoutMs,
    boolean retryOnConnectFailure,
    boolean retryOnConnectionReset,
    boolean retryOnDnsFailure,
    boolean retryOnResponseTimeout,
    List<String> retryableMethods,
    boolean requireIdempotencyKeyForUnsafeMethods,
    int budgetPercent,
    int budgetMinRetriesPerSecond,
    int budgetWindowSeconds) {}
