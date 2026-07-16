package com.autoapi.controlplane.configversion;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.UUID;

@JsonPropertyOrder({"id", "name", "weight", "priority", "primary", "upstreamPool"})
public record CompiledTrafficSplitDestinationSection(
    UUID id,
    String name,
    int weight,
    int priority,
    boolean primary,
    CompiledUpstreamPoolSection upstreamPool) {}
