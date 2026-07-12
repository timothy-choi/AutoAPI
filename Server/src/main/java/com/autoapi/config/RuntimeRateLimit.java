package com.autoapi.config;

import java.util.UUID;

public record RuntimeRateLimit(
    UUID policyId,
    int limitCount,
    int windowSeconds,
    String identitySource,
    String redisFailureMode) {}
