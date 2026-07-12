package com.autoapi.gateway.health;

import io.netty.channel.ConnectTimeoutException;
import io.netty.handler.timeout.ReadTimeoutException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.channels.ClosedChannelException;
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
    if (cause instanceof ConnectException) {
      return Optional.of(FailureCategory.CONNECTION_REFUSED);
    }
    if (cause instanceof ConnectTimeoutException) {
      return Optional.of(FailureCategory.CONNECTION_TIMEOUT);
    }
    if (cause instanceof ReadTimeoutException || cause instanceof SocketTimeoutException) {
      return Optional.of(
          cause instanceof ReadTimeoutException
              ? FailureCategory.RESPONSE_TIMEOUT
              : FailureCategory.CONNECTION_TIMEOUT);
    }
    if (cause instanceof PrematureCloseException) {
      return Optional.of(FailureCategory.PREMATURE_UPSTREAM_CLOSE);
    }
    if (cause instanceof ClosedChannelException) {
      return Optional.of(FailureCategory.CONNECTION_RESET);
    }
    if (cause instanceof SocketException socketException) {
      String message = socketException.getMessage();
      if (message != null && message.toLowerCase().contains("connection reset")) {
        return Optional.of(FailureCategory.CONNECTION_RESET);
      }
    }
    return Optional.empty();
  }
}
