package com.autoapi.controlplane.configversion;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.List;
import java.util.UUID;

@JsonPropertyOrder({"id", "host", "pathPrefix", "methods", "upstreamPool"})
public record CompiledRouteSection(
    UUID id,
    String host,
    String pathPrefix,
    List<String> methods,
    CompiledUpstreamPoolSection upstreamPool) {}
