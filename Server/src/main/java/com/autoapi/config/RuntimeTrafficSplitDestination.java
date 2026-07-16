package com.autoapi.config;

import java.util.UUID;

public record RuntimeTrafficSplitDestination(
    UUID destinationId,
    String name,
    int weight,
    int priority,
    boolean primary,
    UpstreamConfig upstreamPool,
    int cumulativeWeightStart,
    int cumulativeWeightEnd) {}
