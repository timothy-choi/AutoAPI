package com.autoapi.config;

public record RuntimeCircuitBreakerFailurePredicate(
    boolean countHttp5xx,
    boolean countConnectFailure,
    boolean countConnectTimeout,
    boolean countReadTimeout,
    boolean countTlsFailure,
    boolean countTransportException,
    boolean countHttp429) {}
