package com.autoapi.gateway.circuitbreaker;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

final class CircuitBreakerTargetState {

  private CircuitBreakerState state = CircuitBreakerState.CLOSED;
  private final Deque<Instant> failureTimestamps = new ArrayDeque<>();
  private Instant openedAt;
  private int halfOpenActiveProbes;
  private int halfOpenSuccesses;

  CircuitBreakerState state() {
    return state;
  }

  Instant openedAt() {
    return openedAt;
  }

  int halfOpenActiveProbes() {
    return halfOpenActiveProbes;
  }

  int halfOpenSuccesses() {
    return halfOpenSuccesses;
  }

  int failureCount() {
    return failureTimestamps.size();
  }

  void pruneFailures(Instant cutoff) {
    while (!failureTimestamps.isEmpty() && failureTimestamps.peekFirst().isBefore(cutoff)) {
      failureTimestamps.removeFirst();
    }
  }

  void recordFailure(Instant now) {
    failureTimestamps.addLast(now);
  }

  void transitionToOpen(Instant now) {
    state = CircuitBreakerState.OPEN;
    openedAt = now;
    halfOpenActiveProbes = 0;
    halfOpenSuccesses = 0;
  }

  void transitionToHalfOpen() {
    state = CircuitBreakerState.HALF_OPEN;
    halfOpenActiveProbes = 0;
    halfOpenSuccesses = 0;
  }

  void transitionToClosed() {
    state = CircuitBreakerState.CLOSED;
    openedAt = null;
    halfOpenActiveProbes = 0;
    halfOpenSuccesses = 0;
    failureTimestamps.clear();
  }

  boolean incrementHalfOpenProbe() {
    halfOpenActiveProbes++;
    return true;
  }

  void releaseHalfOpenProbe() {
    if (halfOpenActiveProbes > 0) {
      halfOpenActiveProbes--;
    }
  }

  void recordHalfOpenSuccess() {
    halfOpenSuccesses++;
  }
}
