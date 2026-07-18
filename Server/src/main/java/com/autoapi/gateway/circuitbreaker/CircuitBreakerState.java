package com.autoapi.gateway.circuitbreaker;

/** Local circuit breaker state for one upstream target. */
public enum CircuitBreakerState {
  CLOSED,
  OPEN,
  HALF_OPEN
}
