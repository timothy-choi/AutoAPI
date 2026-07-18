package com.autoapi.controlplane.configversion;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.List;
import java.util.UUID;

@JsonPropertyOrder({
  "policyId",
  "selectionKey",
  "selectionKeyName",
  "fallbackMode",
  "fingerprint",
  "destinations"
})
public record CompiledTrafficSplitSection(
    UUID policyId,
    String selectionKey,
    String selectionKeyName,
    String fallbackMode,
    String fingerprint,
    List<CompiledTrafficSplitDestinationSection> destinations) {}
