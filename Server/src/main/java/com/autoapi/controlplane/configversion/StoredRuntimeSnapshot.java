package com.autoapi.controlplane.configversion;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.List;
import java.util.UUID;

@JsonPropertyOrder({
  "apiId",
  "version",
  "contentHash",
  "gateway",
  "routes",
  "apiKeys",
  "observabilityMetadata",
  "effectivePolicies"
})
public record StoredRuntimeSnapshot(
    UUID apiId,
    long version,
    String contentHash,
    CompiledGatewaySection gateway,
    List<CompiledRouteSection> routes,
    List<CompiledApiKeySection> apiKeys,
    CompiledObservabilityMetadataSection observabilityMetadata,
    List<CompiledEffectivePolicyRouteSection> effectivePolicies) {}
