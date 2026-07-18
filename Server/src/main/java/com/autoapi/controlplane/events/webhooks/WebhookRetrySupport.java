package com.autoapi.controlplane.events.webhooks;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public final class WebhookRetrySupport {

  private WebhookRetrySupport() {}

  public static boolean isRetryableStatusCode(int statusCode) {
    if (statusCode >= 200 && statusCode < 300) {
      return false;
    }
    if (statusCode == 408 || statusCode == 425 || statusCode == 429) {
      return true;
    }
    if (statusCode >= 500 && statusCode < 600) {
      return true;
    }
    return false;
  }

  public static boolean isSuccessStatusCode(int statusCode) {
    return statusCode >= 200 && statusCode < 300;
  }

  public static Duration computeBackoff(
      int attemptNumber, Duration initialBackoff, Duration maxBackoff) {
    long multiplier = 1L << Math.min(attemptNumber - 1, 10);
    long delayMs = Math.min(initialBackoff.toMillis() * multiplier, maxBackoff.toMillis());
    return Duration.ofMillis(Math.max(delayMs, initialBackoff.toMillis()));
  }

  public static Optional<Duration> parseRetryAfter(String headerValue, Duration maxBackoff) {
    if (headerValue == null || headerValue.isBlank()) {
      return Optional.empty();
    }
    try {
      long seconds = Long.parseLong(headerValue.trim());
      if (seconds < 0) {
        return Optional.empty();
      }
      return Optional.of(Duration.ofSeconds(Math.min(seconds, maxBackoff.getSeconds())));
    } catch (NumberFormatException ex) {
      return Optional.empty();
    }
  }

  public static Instant nextAttemptInstant(ClockProvider clock, Duration delay) {
    return clock.now().plus(delay);
  }

  @FunctionalInterface
  public interface ClockProvider {
    Instant now();
  }
}
