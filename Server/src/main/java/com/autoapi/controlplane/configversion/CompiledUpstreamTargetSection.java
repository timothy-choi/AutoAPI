package com.autoapi.controlplane.configversion;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.UUID;

@JsonPropertyOrder({"id", "url", "weight"})
public record CompiledUpstreamTargetSection(UUID id, String url, int weight) {}
