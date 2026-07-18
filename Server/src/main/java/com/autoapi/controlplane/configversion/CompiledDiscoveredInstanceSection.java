package com.autoapi.controlplane.configversion;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.UUID;

@JsonPropertyOrder({
  "targetId",
  "instanceId",
  "url",
  "weight",
  "zone",
  "region",
  "registrationEpoch"
})
public record CompiledDiscoveredInstanceSection(
    UUID targetId,
    String instanceId,
    String url,
    int weight,
    String zone,
    String region,
    long registrationEpoch) {}
