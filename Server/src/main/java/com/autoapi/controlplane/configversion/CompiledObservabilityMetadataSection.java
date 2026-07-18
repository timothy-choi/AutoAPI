package com.autoapi.controlplane.configversion;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.Map;

@JsonPropertyOrder({
  "compiledAt",
  "configurationVersion",
  "routeCount",
  "targetCount",
  "serviceCount",
  "serviceInstanceCount",
  "discoveryMembershipVersion",
  "policyCounts"
})
public record CompiledObservabilityMetadataSection(
    String compiledAt,
    long configurationVersion,
    int routeCount,
    int targetCount,
    int serviceCount,
    int serviceInstanceCount,
    long discoveryMembershipVersion,
    Map<String, Integer> policyCounts) {}
