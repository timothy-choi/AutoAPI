package com.autoapi.controlplane.configversion;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.List;
import java.util.UUID;

@JsonPropertyOrder({"apiId", "gateway", "routes", "apiKeys"})
public record HashableRuntimePayload(
    UUID apiId,
    CompiledGatewaySection gateway,
    List<CompiledRouteSection> routes,
    List<CompiledApiKeySection> apiKeys) {}
