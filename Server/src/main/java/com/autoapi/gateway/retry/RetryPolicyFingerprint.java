package com.autoapi.gateway.retry;

import com.autoapi.config.RuntimeRetryPolicyConfig;
import java.util.Arrays;
import java.util.UUID;

/** Fingerprint of immutable retry-policy parameters for budget reconciliation. */
public record RetryPolicyFingerprint(
    UUID policyId,
    int maxAttempts,
    int perAttemptTimeoutMs,
    boolean retryOnConnectFailure,
    boolean retryOnConnectionReset,
    boolean retryOnDnsFailure,
    boolean retryOnResponseTimeout,
    String[] retryableMethods,
    boolean requireIdempotencyKeyForUnsafeMethods,
    int budgetPercent,
    int budgetMinRetriesPerSecond,
    int budgetWindowSeconds) {

  public static RetryPolicyFingerprint from(RuntimeRetryPolicyConfig config) {
    String[] methods = config.retryableMethods().stream().sorted().toArray(String[]::new);
    return new RetryPolicyFingerprint(
        config.policyId(),
        config.maxAttempts(),
        config.perAttemptTimeoutMs(),
        config.retryOnConnectFailure(),
        config.retryOnConnectionReset(),
        config.retryOnDnsFailure(),
        config.retryOnResponseTimeout(),
        methods,
        config.requireIdempotencyKeyForUnsafeMethods(),
        config.budgetPercent(),
        config.budgetMinRetriesPerSecond(),
        config.budgetWindowSeconds());
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof RetryPolicyFingerprint that)) {
      return false;
    }
    return policyId.equals(that.policyId)
        && maxAttempts == that.maxAttempts
        && perAttemptTimeoutMs == that.perAttemptTimeoutMs
        && retryOnConnectFailure == that.retryOnConnectFailure
        && retryOnConnectionReset == that.retryOnConnectionReset
        && retryOnDnsFailure == that.retryOnDnsFailure
        && retryOnResponseTimeout == that.retryOnResponseTimeout
        && requireIdempotencyKeyForUnsafeMethods == that.requireIdempotencyKeyForUnsafeMethods
        && budgetPercent == that.budgetPercent
        && budgetMinRetriesPerSecond == that.budgetMinRetriesPerSecond
        && budgetWindowSeconds == that.budgetWindowSeconds
        && Arrays.equals(retryableMethods, that.retryableMethods);
  }

  @Override
  public int hashCode() {
    int result = policyId.hashCode();
    result = 31 * result + Arrays.hashCode(retryableMethods);
    return result;
  }
}
