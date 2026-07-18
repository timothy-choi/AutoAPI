package com.autoapi.config;

import java.net.URI;
import java.util.UUID;

public record RuntimeDiscoveredInstance(
    UUID targetId,
    String instanceId,
    URI url,
    int weight,
    String zone,
    String region,
    long registrationEpoch) {}
