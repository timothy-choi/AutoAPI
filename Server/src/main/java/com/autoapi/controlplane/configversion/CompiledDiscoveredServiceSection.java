package com.autoapi.controlplane.configversion;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.List;
import java.util.UUID;

@JsonPropertyOrder({
  "serviceId",
  "selectionStrategy",
  "consistentHashKey",
  "consistentHashKeyName",
  "membershipVersion",
  "instances"
})
public record CompiledDiscoveredServiceSection(
    UUID serviceId,
    String selectionStrategy,
    String consistentHashKey,
    String consistentHashKeyName,
    long membershipVersion,
    List<CompiledDiscoveredInstanceSection> instances) {}
