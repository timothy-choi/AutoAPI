package com.autoapi.gateway.retry;

/** Retry-eligible transport failure categories for Phase 6. */
public enum RetryFailureCategory {
  CONNECT_FAILURE,
  CONNECTION_RESET,
  DNS_FAILURE,
  RESPONSE_TIMEOUT
}
