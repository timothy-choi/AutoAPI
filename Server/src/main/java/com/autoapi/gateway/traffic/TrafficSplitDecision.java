package com.autoapi.gateway.traffic;

import com.autoapi.config.RuntimeDiscoveredServiceConfig;
import com.autoapi.config.UpstreamConfig;
import java.util.UUID;

public record TrafficSplitDecision(
    UUID policyId,
    UUID destinationId,
    String destinationName,
    UpstreamConfig upstreamPool,
    RuntimeDiscoveredServiceConfig discoveredService,
    String selectionKeySource,
    int bucket,
    int totalWeight,
    UUID nominalDestinationId,
    String nominalDestinationName,
    boolean fallbackUsed,
    TrafficSplitFallbackResolver.FallbackReason fallbackReason) {}
