package com.autoapi.config;

import java.util.List;
import java.util.UUID;

/**
 * Compiled retry policy attached to a route. {@code maxAttempts} is the total upstream attempts
 * including the first attempt ({@code 1} disables retries).
 */
public record RuntimeRetryPolicyConfig(
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
    int budgetWindowSeconds) {

  public RuntimeRetryPolicyConfig {
    retryableMethods = List.copyOf(retryableMethods);
  }

  public boolean retriesEnabled() {
    return maxAttempts > 1;
  }
}
