package com.autoapi.gateway.health;

/** Qualifying transport failure categories for passive outlier detection. */
public enum FailureCategory {
  CONNECTION_REFUSED,
  CONNECTION_RESET,
  DNS_FAILURE,
  CONNECTION_TIMEOUT,
  RESPONSE_TIMEOUT,
  PREMATURE_UPSTREAM_CLOSE
}
