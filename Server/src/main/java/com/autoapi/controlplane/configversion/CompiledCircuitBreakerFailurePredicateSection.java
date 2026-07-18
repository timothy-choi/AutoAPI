package com.autoapi.controlplane.configversion;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({
  "countHttp5xx",
  "countConnectFailure",
  "countConnectTimeout",
  "countReadTimeout",
  "countTlsFailure",
  "countTransportException",
  "countHttp429"
})
public record CompiledCircuitBreakerFailurePredicateSection(
    boolean countHttp5xx,
    boolean countConnectFailure,
    boolean countConnectTimeout,
    boolean countReadTimeout,
    boolean countTlsFailure,
    boolean countTransportException,
    boolean countHttp429) {}
