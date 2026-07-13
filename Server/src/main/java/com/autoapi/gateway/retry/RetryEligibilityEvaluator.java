package com.autoapi.gateway.retry;

import com.autoapi.config.RuntimeRetryPolicyConfig;
import org.springframework.http.HttpMethod;

public final class RetryEligibilityEvaluator {

  public enum DenialReason {
    METHOD_NOT_RETRYABLE,
    MISSING_IDEMPOTENCY_KEY,
    INVALID_IDEMPOTENCY_KEY,
    FAILURE_NOT_RETRYABLE,
    MAX_ATTEMPTS_REACHED,
    BUDGET_EXHAUSTED,
    BODY_NOT_REPLAYABLE,
    CLIENT_CANCELLED,
    NO_ELIGIBLE_TARGET
  }

  private RetryEligibilityEvaluator() {}

  public static boolean isMethodRetryable(
      RuntimeRetryPolicyConfig policy, HttpMethod method, boolean hasValidIdempotencyKey) {
    if (policy == null || method == null) {
      return false;
    }
    String methodName = method.name();
    boolean listed = policy.retryableMethods().stream().anyMatch(methodName::equalsIgnoreCase);
    if (!listed) {
      return false;
    }
    if (isUnsafeMethod(methodName)) {
      return policy.requireIdempotencyKeyForUnsafeMethods() && hasValidIdempotencyKey;
    }
    return true;
  }

  public static boolean isFailureRetryable(
      RuntimeRetryPolicyConfig policy, RetryFailureCategory category) {
    if (policy == null || category == null) {
      return false;
    }
    return switch (category) {
      case CONNECT_FAILURE -> policy.retryOnConnectFailure();
      case CONNECTION_RESET -> policy.retryOnConnectionReset();
      case DNS_FAILURE -> policy.retryOnDnsFailure();
      case RESPONSE_TIMEOUT -> policy.retryOnResponseTimeout();
    };
  }

  private static boolean isUnsafeMethod(String method) {
    return "POST".equals(method) || "PATCH".equals(method);
  }
}
