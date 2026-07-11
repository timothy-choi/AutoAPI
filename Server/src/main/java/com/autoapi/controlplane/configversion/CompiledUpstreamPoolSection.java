package com.autoapi.controlplane.configversion;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.List;
import java.util.UUID;

@JsonPropertyOrder({"id", "algorithm", "targets"})
public record CompiledUpstreamPoolSection(
    UUID id, String algorithm, List<CompiledUpstreamTargetSection> targets) {}
