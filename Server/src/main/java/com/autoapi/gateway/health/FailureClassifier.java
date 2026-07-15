package com.autoapi.gateway.health;

import io.netty.channel.ConnectTimeoutException;
import io.netty.handler.timeout.ReadTimeoutException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.PortUnreachableException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.channels.ClosedChannelException;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.Exceptions;
import reactor.netty.http.client.PrematureCloseException;

/**
 * Classifies transport failures for passive health tracking. HTTP responses (including 4xx and 5xx)
 * and client-side cancellations are not qualifying failures in Phase 5.
 */
public final class FailureClassifier {

  public Optional<FailureCategory> classifyTransportFailure(Throwable error) {
    if (error == null) {
      return Optional.empty();
    }
    if (isClientCancel(error)) {
      return Optional.empty();
    }
    Throwable current = error;
    while (current != null) {
      Optional<FailureCategory> category = classifyCause(current);
      if (category.isPresent()) {
        return category;
      }
      current = current.getCause();
    }
    return Optional.empty();
  }

  public Optional<FailureCategory> classifyHttpStatus(HttpStatusCode status) {
    return Optional.empty();
  }

  /**
   * Resolves a qualifying passive-health category for upstream transport failures. When a {@link
   * WebClientRequestException} is not a client cancellation and no specific cause matches, the
   * failure still qualifies as {@link FailureCategory#CONNECTION_REFUSED} so controlled 502
   * responses always produce passive-health accounting.
   */
  public Optional<FailureCategory> resolveQualifyingCategory(Throwable error) {
    Optional<FailureCategory> classified = classifyTransportFailure(error);
    if (classified.isPresent()) {
      return classified;
    }
    if (isUpstreamTransportFailure(error)) {
      return Optional.of(FailureCategory.CONNECTION_REFUSED);
    }
    return Optional.empty();
  }

  private static boolean isUpstreamTransportFailure(Throwable error) {
    if (error == null || isClientCancel(error)) {
      return false;
    }
    Throwable current = error;
    while (current != null) {
      if (current instanceof WebClientRequestException) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }

  private static boolean isClientCancel(Throwable error) {
    if (Exceptions.isCancel(error)) {
      return true;
    }
    Throwable current = error;
    while (current != null) {
      if (current instanceof CancellationException) {
        return true;
      }
      if (current instanceof WebClientRequestException requestException
          && requestException.getCause() instanceof CancellationException) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }

  private static Optional<FailureCategory> classifyCause(Throwable cause) {
    if (cause instanceof UnknownHostException) {
      return Optional.of(FailureCategory.DNS_FAILURE);
    }
    if (cause instanceof ConnectTimeoutException) {
      return Optional.of(FailureCategory.CONNECTION_TIMEOUT);
    }
    if (cause instanceof ConnectException || cause instanceof NoRouteToHostException) {
      return Optional.of(FailureCategory.CONNECTION_REFUSED);
    }
    if (cause instanceof PortUnreachableException) {
      return Optional.of(FailureCategory.CONNECTION_REFUSED);
    }
    if (cause instanceof ReadTimeoutException || cause instanceof SocketTimeoutException) {
      return Optional.of(
          cause instanceof ReadTimeoutException
              ? FailureCategory.RESPONSE_TIMEOUT
              : FailureCategory.CONNECTION_TIMEOUT);
    }
    if (cause instanceof java.util.concurrent.TimeoutException) {
      return Optional.of(FailureCategory.RESPONSE_TIMEOUT);
    }
    if (cause instanceof PrematureCloseException) {
      return Optional.of(FailureCategory.PREMATURE_UPSTREAM_CLOSE);
    }
    if (cause instanceof ClosedChannelException) {
      return Optional.of(FailureCategory.CONNECTION_RESET);
    }
    if (cause instanceof SocketException socketException) {
      String message = socketException.getMessage();
      if (message == null || message.isBlank()) {
        return Optional.empty();
      }
      String normalized = message.toLowerCase(Locale.ROOT);
      if (normalized.contains("connection reset")) {
        return Optional.of(FailureCategory.CONNECTION_RESET);
      }
      if (normalized.contains("network is unreachable")
          || normalized.contains("no route to host")
          || normalized.contains("host is down")) {
        return Optional.of(FailureCategory.CONNECTION_REFUSED);
      }
    }
    return Optional.empty();
  }
}
