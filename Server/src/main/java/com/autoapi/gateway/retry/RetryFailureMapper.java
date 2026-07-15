package com.autoapi.gateway.retry;

import com.autoapi.gateway.health.FailureCategory;
import java.util.Optional;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.Exceptions;

/** Maps Phase 5 passive-health categories to Phase 6 retry categories. */
public final class RetryFailureMapper {

  private RetryFailureMapper() {}

  public static Optional<RetryFailureCategory> toRetryCategory(FailureCategory category) {
    if (category == null) {
      return Optional.empty();
    }
    return switch (category) {
      case CONNECTION_REFUSED, CONNECTION_TIMEOUT ->
          Optional.of(RetryFailureCategory.CONNECT_FAILURE);
      case CONNECTION_RESET, PREMATURE_UPSTREAM_CLOSE ->
          Optional.of(RetryFailureCategory.CONNECTION_RESET);
      case DNS_FAILURE -> Optional.of(RetryFailureCategory.DNS_FAILURE);
      case RESPONSE_TIMEOUT -> Optional.of(RetryFailureCategory.RESPONSE_TIMEOUT);
    };
  }

  public static boolean isResponseTimeout(Throwable error) {
    if (error == null) {
      return false;
    }
    Throwable current = error;
    while (current != null) {
      if (Exceptions.isRetryExhausted(current) && current.getCause() != null) {
        current = current.getCause();
        continue;
      }
      if (current instanceof java.util.concurrent.TimeoutException) {
        return true;
      }
      if (current.getClass().getSimpleName().contains("TimeoutException")) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }

  public static boolean isClientCancellation(Throwable error) {
    if (error == null) {
      return false;
    }
    if (Exceptions.isCancel(error)) {
      return true;
    }
    Throwable current = error;
    while (current != null) {
      if (current instanceof java.util.concurrent.CancellationException) {
        return true;
      }
      if (current instanceof WebClientRequestException requestException
          && requestException.getCause() instanceof java.util.concurrent.CancellationException) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }
}
