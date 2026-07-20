package com.autoapi.controlplane.policy;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Registry of supported policy types and their merge strategies.
 *
 * <p>Merge strategies:
 *
 * <ul>
 *   <li>{@code rateLimit} — {@link PolicyMergeStrategy#OVERRIDE}: most specific scope wins.
 *   <li>{@code retry} — {@link PolicyMergeStrategy#OVERRIDE}: most specific scope wins.
 *   <li>{@code circuitBreaker} — {@link PolicyMergeStrategy#OVERRIDE}: most specific scope wins.
 *   <li>{@code backendHealth} — {@link PolicyMergeStrategy#OVERRIDE}: most specific scope wins.
 *   <li>{@code trafficSplit} — {@link PolicyMergeStrategy#OVERRIDE}: most specific scope wins.
 *   <li>{@code timeout} — {@link PolicyMergeStrategy#OVERRIDE}: most specific scope wins.
 *   <li>{@code headers} — {@link PolicyMergeStrategy#MERGE_MAP}: headers merged by name.
 *   <li>{@code cors} — {@link PolicyMergeStrategy#OVERRIDE}: most specific scope wins.
 *   <li>{@code logging} — {@link PolicyMergeStrategy#MERGE_MAP}: logging fields merged.
 *   <li>{@code authentication} — {@link PolicyMergeStrategy#OVERRIDE}: most specific scope wins.
 *   <li>{@code requestValidation} — {@link PolicyMergeStrategy#MERGE_MAP}: validation rules merged.
 * </ul>
 */
@Component
@ConditionalOnProperty(
    name = "autoapi.controlplane.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class PolicyTypeRegistry {

  private final Map<String, PolicyTypeDefinition> definitions;

  public PolicyTypeRegistry() {
    Map<String, PolicyTypeDefinition> map = new LinkedHashMap<>();
    register(map, "rateLimit", PolicyMergeStrategy.OVERRIDE);
    register(map, "retry", PolicyMergeStrategy.OVERRIDE);
    register(map, "circuitBreaker", PolicyMergeStrategy.OVERRIDE);
    register(map, "backendHealth", PolicyMergeStrategy.OVERRIDE);
    register(map, "trafficSplit", PolicyMergeStrategy.OVERRIDE);
    register(map, "timeout", PolicyMergeStrategy.OVERRIDE);
    register(map, "headers", PolicyMergeStrategy.MERGE_MAP);
    register(map, "cors", PolicyMergeStrategy.OVERRIDE);
    register(map, "logging", PolicyMergeStrategy.MERGE_MAP);
    register(map, "authentication", PolicyMergeStrategy.OVERRIDE);
    register(map, "requestValidation", PolicyMergeStrategy.MERGE_MAP);
    this.definitions = Collections.unmodifiableMap(map);
  }

  private static void register(
      Map<String, PolicyTypeDefinition> map, String typeName, PolicyMergeStrategy strategy) {
    map.put(typeName, new PolicyTypeDefinition(typeName, strategy));
  }

  public Optional<PolicyTypeDefinition> find(String policyType) {
    return Optional.ofNullable(definitions.get(policyType));
  }

  public PolicyTypeDefinition require(String policyType) {
    PolicyTypeDefinition definition = definitions.get(policyType);
    if (definition == null) {
      throw new IllegalArgumentException("Unknown policy type: " + policyType);
    }
    return definition;
  }

  public Map<String, PolicyTypeDefinition> all() {
    return definitions;
  }
}
