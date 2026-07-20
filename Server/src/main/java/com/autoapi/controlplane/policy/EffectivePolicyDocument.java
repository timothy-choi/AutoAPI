package com.autoapi.controlplane.policy;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;

/** Resolved effective policy for a scope. */
public record EffectivePolicyDocument(
    @JsonUnwrapped Map<String, JsonNode> policies, List<PolicyExplainEntry> explanations) {

  public static EffectivePolicyDocument ofPolicies(Map<String, JsonNode> policies) {
    return new EffectivePolicyDocument(policies, List.of());
  }
}
