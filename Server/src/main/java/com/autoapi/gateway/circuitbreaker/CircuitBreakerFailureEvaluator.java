package com.autoapi.gateway.circuitbreaker;

import com.autoapi.config.RuntimeCircuitBreakerFailurePredicate;
import com.autoapi.config.RuntimeCircuitBreakerPolicyConfig;
import com.autoapi.gateway.health.FailureCategory;
import com.autoapi.gateway.retry.RetryFailureCategory;
import org.springframework.http.HttpStatusCode;

/** Evaluates whether an upstream outcome qualifies as a circuit breaker failure. */
public final class CircuitBreakerFailureEvaluator {

  private CircuitBreakerFailureEvaluator() {}

  public static boolean isQualifyingHttpStatus(
      HttpStatusCode statusCode, RuntimeCircuitBreakerFailurePredicate predicate) {
    if (statusCode == null) {
      return false;
    }
    int code = statusCode.value();
    if (predicate.countHttp429() && code == 429) {
      return true;
    }
    return predicate.countHttp5xx() && code >= 500 && code <= 599;
  }

  public static boolean isQualifyingTransportFailure(
      FailureCategory category, RuntimeCircuitBreakerFailurePredicate predicate) {
    if (category == null) {
      return predicate.countTransportException();
    }
    return switch (category) {
      case CONNECTION_REFUSED -> predicate.countConnectFailure();
      case CONNECTION_TIMEOUT -> predicate.countConnectTimeout();
      case RESPONSE_TIMEOUT -> predicate.countReadTimeout();
      case CONNECTION_RESET, DNS_FAILURE, PREMATURE_UPSTREAM_CLOSE ->
          predicate.countTransportException();
    };
  }

  public static boolean isQualifyingRetryFailure(
      RetryFailureCategory category,
      RuntimeCircuitBreakerFailurePredicate predicate,
      RuntimeCircuitBreakerPolicyConfig policy) {
    if (category == null) {
      return predicate.countTransportException();
    }
    return switch (category) {
      case CONNECT_FAILURE -> predicate.countConnectFailure();
      case CONNECTION_RESET -> predicate.countTransportException();
      case DNS_FAILURE -> predicate.countTransportException();
      case RESPONSE_TIMEOUT -> predicate.countReadTimeout();
    };
  }

  public static RuntimeCircuitBreakerFailurePredicate predicate(
      RuntimeCircuitBreakerPolicyConfig policy) {
    return policy == null ? null : policy.failurePredicate();
  }
}
