package com.autoapi.gateway.circuitbreaker;

/** Raised when every candidate upstream target is rejected by an open circuit breaker. */
public final class CircuitBreakerOpenException extends RuntimeException {

  public CircuitBreakerOpenException(String message) {
    super(message);
  }
}
