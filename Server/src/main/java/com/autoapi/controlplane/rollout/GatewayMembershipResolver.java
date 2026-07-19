package com.autoapi.controlplane.rollout;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Resolves gateway group membership with explicit precedence over selector matching. */
public final class GatewayMembershipResolver {

  public enum MembershipKind {
    UNASSIGNED,
    SELECTOR,
    EXPLICIT_INCLUDE,
    EXPLICIT_EXCLUDE
  }

  private GatewayMembershipResolver() {}

  public static boolean selectorMatches(JsonNode selector, Map<String, String> effectiveLabels) {
    if (selector == null || selector.isNull() || selector.isEmpty()) {
      return effectiveLabels != null && !effectiveLabels.isEmpty();
    }
    JsonNode matchLabels = selector.get("matchLabels");
    if (matchLabels == null || !matchLabels.isObject() || matchLabels.isEmpty()) {
      return false;
    }
    var fields = matchLabels.fields();
    while (fields.hasNext()) {
      var entry = fields.next();
      String expected = entry.getValue().asText();
      String actual = effectiveLabels.get(entry.getKey());
      if (actual == null || !actual.equals(expected)) {
        return false;
      }
    }
    return true;
  }

  public static ResolvedMembership resolve(
      String gatewayId,
      Map<String, String> effectiveLabels,
      List<GroupMembershipContext> groups,
      Map<String, ExplicitMembership> explicitByGateway) {
    ExplicitMembership explicit = explicitByGateway.get(gatewayId);
    if (explicit != null && explicit.membershipType() == MembershipKind.EXPLICIT_EXCLUDE) {
      return new ResolvedMembership(
          gatewayId, null, MembershipKind.EXPLICIT_EXCLUDE, explicit.gatewayGroupId());
    }
    if (explicit != null && explicit.membershipType() == MembershipKind.EXPLICIT_INCLUDE) {
      return new ResolvedMembership(
          gatewayId,
          findGroup(groups, explicit.gatewayGroupId()),
          MembershipKind.EXPLICIT_INCLUDE,
          explicit.gatewayGroupId());
    }
    List<GroupMembershipContext> selectorMatches = new ArrayList<>();
    for (GroupMembershipContext group : groups) {
      if (!group.enabled()) {
        continue;
      }
      if (selectorMatches(group.selector(), effectiveLabels)) {
        selectorMatches.add(group);
      }
    }
    if (selectorMatches.isEmpty()) {
      return new ResolvedMembership(gatewayId, null, MembershipKind.UNASSIGNED, null);
    }
    selectorMatches.sort(Comparator.comparing(GroupMembershipContext::name));
    GroupMembershipContext selected = selectorMatches.get(0);
    return new ResolvedMembership(
        gatewayId, selected, MembershipKind.SELECTOR, selected.gatewayGroupId());
  }

  public static Map<String, String> parseLabels(ObjectMapper objectMapper, String json) {
    if (json == null || json.isBlank()) {
      return Map.of();
    }
    try {
      JsonNode node = objectMapper.readTree(json);
      Map<String, String> labels = new LinkedHashMap<>();
      node.fields()
          .forEachRemaining(entry -> labels.put(entry.getKey(), entry.getValue().asText()));
      return Map.copyOf(labels);
    } catch (Exception ex) {
      return Map.of();
    }
  }

  private static GroupMembershipContext findGroup(
      List<GroupMembershipContext> groups, UUID groupId) {
    return groups.stream()
        .filter(group -> group.gatewayGroupId().equals(groupId))
        .findFirst()
        .orElse(null);
  }

  public record GroupMembershipContext(
      UUID gatewayGroupId,
      UUID projectId,
      UUID apiId,
      String name,
      boolean enabled,
      JsonNode selector,
      Long desiredConfigVersion) {}

  public record ExplicitMembership(UUID gatewayGroupId, MembershipKind membershipType) {}

  public record ResolvedMembership(
      String gatewayId,
      GroupMembershipContext group,
      MembershipKind membershipKind,
      UUID gatewayGroupId) {}
}
