package com.autoapi.gateway.circuitbreaker;

/** Result of attempting to admit a request against a target circuit breaker. */
public enum CircuitAdmission {
  ALLOW,
  REJECT_OPEN,
  REJECT_HALF_OPEN_FULL
}
