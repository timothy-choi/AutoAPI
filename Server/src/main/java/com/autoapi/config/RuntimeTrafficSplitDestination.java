package com.autoapi.config;

import java.util.UUID;

public record RuntimeTrafficSplitDestination(
    UUID destinationId,
    String name,
    int weight,
    int priority,
    boolean primary,
    UpstreamConfig upstreamPool,
    RuntimeDiscoveredServiceConfig discoveredService,
    int cumulativeWeightStart,
    int cumulativeWeightEnd) {}
