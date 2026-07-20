package com.autoapi.controlplane.policy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Merges policy contributions by type using registered merge strategies. */
@Component
@ConditionalOnProperty(
    name = "autoapi.controlplane.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class PolicyMergeEngine {

  private final PolicyTypeRegistry typeRegistry;
  private final ObjectMapper objectMapper;

  public PolicyMergeEngine(PolicyTypeRegistry typeRegistry, ObjectMapper objectMapper) {
    this.typeRegistry = typeRegistry;
    this.objectMapper = objectMapper;
  }

  public EffectivePolicyDocument merge(List<PolicyContribution> contributions, boolean explain) {
    Map<String, List<PolicyContribution>> byType = new LinkedHashMap<>();
    for (PolicyContribution contribution : contributions) {
      byType
          .computeIfAbsent(contribution.policyType(), ignored -> new ArrayList<>())
          .add(contribution);
    }

    Map<String, JsonNode> policies = new LinkedHashMap<>();
    List<PolicyExplainEntry> explanations = explain ? new ArrayList<>() : List.of();

    for (Map.Entry<String, List<PolicyContribution>> entry : byType.entrySet()) {
      String policyType = entry.getKey();
      List<PolicyContribution> sorted =
          entry.getValue().stream()
              .sorted(
                  Comparator.comparingInt((PolicyContribution c) -> c.sourceLevel().order())
                      .thenComparing(
                          PolicyContribution::sourceId,
                          Comparator.nullsFirst(Comparator.naturalOrder())))
              .toList();

      PolicyTypeDefinition definition = typeRegistry.find(policyType).orElse(null);
      PolicyMergeStrategy strategy =
          definition == null ? PolicyMergeStrategy.OVERRIDE : definition.mergeStrategy();

      MergeResult result = mergeContributions(sorted, strategy);
      if (result.value() != null && !result.value().isNull()) {
        policies.put(policyType, result.value());
      }
      if (explain && result.winning() != null) {
        explanations.add(
            new PolicyExplainEntry(
                policyType,
                result.winning().sourceLevel(),
                result.winning().sourceId(),
                result.winning().sourceName(),
                result.winning().bundleRevision(),
                strategy,
                result.value(),
                sorted.size()));
      }
    }

    return new EffectivePolicyDocument(policies, explanations);
  }

  private MergeResult mergeContributions(
      List<PolicyContribution> contributions, PolicyMergeStrategy strategy) {
    if (contributions.isEmpty()) {
      return new MergeResult(null, null);
    }

    PolicyContribution last = contributions.get(contributions.size() - 1);

    if (strategy == PolicyMergeStrategy.DISABLE) {
      boolean disabled =
          contributions.stream().anyMatch(c -> c.value() == null || c.value().isNull());
      return disabled ? new MergeResult(null, last) : new MergeResult(last.value(), last);
    }

    boolean disabled =
        contributions.stream().anyMatch(c -> c.value() == null || c.value().isNull());
    if (disabled) {
      return new MergeResult(null, last);
    }

    return switch (strategy) {
      case OVERRIDE -> new MergeResult(last.value(), last);
      case MERGE_MAP -> new MergeResult(mergeMaps(contributions), last);
      case APPEND_LIST -> new MergeResult(appendLists(contributions), last);
      case DISABLE -> new MergeResult(null, last);
    };
  }

  private JsonNode mergeMaps(List<PolicyContribution> contributions) {
    ObjectNode merged = objectMapper.createObjectNode();
    for (PolicyContribution contribution : contributions) {
      if (contribution.value() == null || contribution.value().isNull()) {
        continue;
      }
      if (contribution.value().isObject()) {
        contribution
            .value()
            .fields()
            .forEachRemaining(field -> merged.set(field.getKey(), field.getValue()));
      } else {
        return contribution.value().deepCopy();
      }
    }
    return merged;
  }

  private JsonNode appendLists(List<PolicyContribution> contributions) {
    ArrayNode merged = objectMapper.createArrayNode();
    for (PolicyContribution contribution : contributions) {
      if (contribution.value() == null || contribution.value().isNull()) {
        continue;
      }
      if (contribution.value().isArray()) {
        contribution.value().forEach(merged::add);
      } else {
        merged.add(contribution.value());
      }
    }
    return merged;
  }

  private record MergeResult(JsonNode value, PolicyContribution winning) {}
}
