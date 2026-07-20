package com.autoapi.controlplane.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PolicyMergeEngineTest {

  private PolicyMergeEngine mergeEngine;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper().findAndRegisterModules();
    mergeEngine = new PolicyMergeEngine(new PolicyTypeRegistry(), objectMapper);
  }

  @Test
  void overrideStrategyUsesMostSpecificScope() {
    ObjectNode orgTimeout = objectMapper.createObjectNode().put("timeoutMs", 10000);
    ObjectNode routeTimeout = objectMapper.createObjectNode().put("timeoutMs", 3000);

    EffectivePolicyDocument document =
        mergeEngine.merge(
            List.of(
                contribution("timeout", orgTimeout, PolicyHierarchyLevel.ORGANIZATION),
                contribution("timeout", routeTimeout, PolicyHierarchyLevel.ROUTE)),
            false);

    assertEquals(3000, document.policies().get("timeout").get("timeoutMs").asInt());
  }

  @Test
  void mergeMapStrategyCombinesHeaders() {
    ObjectNode orgHeaders = objectMapper.createObjectNode().put("X-A", "foo");
    ObjectNode projectHeaders = objectMapper.createObjectNode().put("X-B", "bar");

    EffectivePolicyDocument document =
        mergeEngine.merge(
            List.of(
                contribution("headers", orgHeaders, PolicyHierarchyLevel.ORGANIZATION),
                contribution("headers", projectHeaders, PolicyHierarchyLevel.PROJECT)),
            false);

    assertEquals("foo", document.policies().get("headers").get("X-A").asText());
    assertEquals("bar", document.policies().get("headers").get("X-B").asText());
  }

  @Test
  void disableModeRemovesPolicyType() {
    ObjectNode rateLimit = objectMapper.createObjectNode().put("limitCount", 1000);

    EffectivePolicyDocument document =
        mergeEngine.merge(
            List.of(
                contribution("rateLimit", rateLimit, PolicyHierarchyLevel.ORGANIZATION),
                new PolicyContribution(
                    "rateLimit",
                    objectMapper.nullNode(),
                    PolicyHierarchyLevel.API,
                    null,
                    "api",
                    0,
                    true)),
            false);

    assertNull(document.policies().get("rateLimit"));
  }

  @Test
  void explainModeCapturesWinningSource() {
    ObjectNode routeTimeout = objectMapper.createObjectNode().put("timeoutMs", 3000);

    EffectivePolicyDocument document =
        mergeEngine.merge(
            List.of(
                contribution(
                    "timeout",
                    objectMapper.createObjectNode().put("timeoutMs", 10000),
                    PolicyHierarchyLevel.ORGANIZATION),
                contribution("timeout", routeTimeout, PolicyHierarchyLevel.ROUTE)),
            true);

    assertEquals(1, document.explanations().size());
    PolicyExplainEntry explain = document.explanations().getFirst();
    assertEquals("timeout", explain.policyType());
    assertEquals(PolicyHierarchyLevel.ROUTE, explain.winningLevel());
    assertEquals(PolicyMergeStrategy.OVERRIDE, explain.mergeStrategy());
  }

  @Test
  void unknownPolicyTypesUseOverrideSemantics() {
    ObjectNode org = objectMapper.createObjectNode().put("enabled", true);
    ObjectNode route = objectMapper.createObjectNode().put("enabled", false);

    EffectivePolicyDocument document =
        mergeEngine.merge(
            List.of(
                contribution("customPolicy", org, PolicyHierarchyLevel.ORGANIZATION),
                contribution("customPolicy", route, PolicyHierarchyLevel.ROUTE)),
            false);

    assertEquals(false, document.policies().get("customPolicy").get("enabled").asBoolean());
  }

  private static PolicyContribution contribution(
      String type, ObjectNode value, PolicyHierarchyLevel level) {
    return new PolicyContribution(type, value, level, null, level.name().toLowerCase(), 0, false);
  }
}
