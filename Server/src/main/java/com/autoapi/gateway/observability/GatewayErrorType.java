package com.autoapi.gateway.observability;

/** Bounded internal error taxonomy for metrics, logs, and traces. */
public enum GatewayErrorType {
  NONE,
  AUTHENTICATION_REJECTED,
  RATE_LIMITED,
  NO_ROUTE,
  NO_ELIGIBLE_TARGET,
  CIRCUIT_OPEN,
  CONNECT_TIMEOUT,
  READ_TIMEOUT,
  CONNECTION_REFUSED,
  TLS_ERROR,
  UPSTREAM_5XX,
  RETRY_EXHAUSTED,
  CLIENT_CANCELLED,
  INTERNAL_ERROR;

  public String metricValue() {
    return name();
  }
}
