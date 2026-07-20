package com.autoapi.controlplane.configversion;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.UUID;

/** Flattened effective policy for a route embedded in gateway runtime snapshots. */
@JsonPropertyOrder({"routeId", "policies"})
public record CompiledEffectivePolicyRouteSection(UUID routeId, Map<String, JsonNode> policies) {}
