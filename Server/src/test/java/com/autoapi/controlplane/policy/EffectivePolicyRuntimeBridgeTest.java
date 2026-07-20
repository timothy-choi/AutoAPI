package com.autoapi.controlplane.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.autoapi.controlplane.configversion.CompiledRateLimitSection;
import com.autoapi.controlplane.configversion.CompiledRouteSection;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EffectivePolicyRuntimeBridgeTest {

  private final EffectivePolicyRuntimeBridge bridge = new EffectivePolicyRuntimeBridge();
  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

  @Test
  void overlaysEffectiveRateLimitOntoCompiledRoute() {
    UUID routeId = UUID.randomUUID();
    CompiledRouteSection route =
        new CompiledRouteSection(
            routeId,
            "api.example.com",
            "/v1",
            List.of("GET"),
            null,
            null,
            null,
            null,
            null,
            null,
            null);

    ObjectNode rateLimit = objectMapper.createObjectNode();
    rateLimit.put("limitCount", 500);
    rateLimit.put("windowSeconds", 60);
    rateLimit.put("identitySource", "API_KEY");
    rateLimit.put("redisFailureMode", "FAIL_OPEN");

    EffectivePolicyDocument document =
        EffectivePolicyDocument.ofPolicies(Map.of("rateLimit", rateLimit));

    List<CompiledRouteSection> merged = bridge.apply(List.of(route), Map.of(routeId, document));

    CompiledRateLimitSection effective = merged.getFirst().rateLimit();
    assertEquals(500, effective.limitCount());
    assertEquals(60, effective.windowSeconds());
  }
}
