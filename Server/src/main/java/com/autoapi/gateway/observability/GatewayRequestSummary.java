package com.autoapi.gateway.observability;

import java.time.Instant;

public record GatewayRequestSummary(
    String requestId,
    String traceId,
    String gatewayId,
    String apiId,
    String routeId,
    String method,
    int status,
    long durationMs,
    int attemptCount,
    int retryCount,
    boolean fallbackUsed,
    Instant timestamp) {}
