package com.autoapi.controlplane.rollout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.autoapi.controlplane.rollout.GatewayMembershipResolver.ExplicitMembership;
import com.autoapi.controlplane.rollout.GatewayMembershipResolver.GroupMembershipContext;
import com.autoapi.controlplane.rollout.GatewayMembershipResolver.MembershipKind;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GatewayMembershipResolverTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final UUID PROJECT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID API_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
  private static final UUID INCLUDE_GROUP_ID =
      UUID.fromString("44444444-4444-4444-4444-444444444444");
  private static final UUID SELECTOR_GROUP_ID =
      UUID.fromString("55555555-5555-5555-5555-555555555555");
  private static final UUID EXCLUDE_GROUP_ID =
      UUID.fromString("66666666-6666-6666-6666-666666666666");

  @Test
  void explicitExcludeTakesPrecedenceOverSelector() {
    Map<String, String> labels = Map.of("region", "us-west", "environment", "production");
    List<GroupMembershipContext> groups =
        List.of(group(SELECTOR_GROUP_ID, "selector-group", selector("region", "us-west")));
    Map<String, ExplicitMembership> explicit =
        Map.of(
            "gateway-a", new ExplicitMembership(EXCLUDE_GROUP_ID, MembershipKind.EXPLICIT_EXCLUDE));

    var resolved = GatewayMembershipResolver.resolve("gateway-a", labels, groups, explicit);

    assertEquals(MembershipKind.EXPLICIT_EXCLUDE, resolved.membershipKind());
    assertEquals(EXCLUDE_GROUP_ID, resolved.gatewayGroupId());
    assertNull(resolved.group());
  }

  @Test
  void explicitIncludeTakesPrecedenceOverSelector() {
    Map<String, String> labels = Map.of("region", "us-west", "environment", "production");
    List<GroupMembershipContext> groups =
        List.of(
            group(SELECTOR_GROUP_ID, "selector-group", selector("region", "us-west")),
            group(INCLUDE_GROUP_ID, "include-group", selector("environment", "staging")));
    Map<String, ExplicitMembership> explicit =
        Map.of(
            "gateway-a", new ExplicitMembership(INCLUDE_GROUP_ID, MembershipKind.EXPLICIT_INCLUDE));

    var resolved = GatewayMembershipResolver.resolve("gateway-a", labels, groups, explicit);

    assertEquals(MembershipKind.EXPLICIT_INCLUDE, resolved.membershipKind());
    assertEquals(INCLUDE_GROUP_ID, resolved.gatewayGroupId());
    assertEquals("include-group", resolved.group().name());
  }

  @Test
  void selectorMatchesWhenNoExplicitMembership() {
    Map<String, String> labels = Map.of("region", "us-west", "environment", "production");
    List<GroupMembershipContext> groups =
        List.of(group(SELECTOR_GROUP_ID, "selector-group", selector("region", "us-west")));

    var resolved = GatewayMembershipResolver.resolve("gateway-a", labels, groups, Map.of());

    assertEquals(MembershipKind.SELECTOR, resolved.membershipKind());
    assertEquals(SELECTOR_GROUP_ID, resolved.gatewayGroupId());
  }

  @Test
  void selectorMatchingRequiresAllMatchLabels() {
    Map<String, String> partial = Map.of("region", "us-west");
    var selector = selector("region", "us-west", "environment", "production");

    assertFalse(GatewayMembershipResolver.selectorMatches(selector, partial));
    assertTrue(
        GatewayMembershipResolver.selectorMatches(
            selector, Map.of("region", "us-west", "environment", "production")));
    assertFalse(
        GatewayMembershipResolver.selectorMatches(
            selector, Map.of("region", "us-west", "environment", "staging")));
  }

  @Test
  void parseLabelsReturnsEmptyMapForInvalidJson() {
    assertEquals(Map.of(), GatewayMembershipResolver.parseLabels(OBJECT_MAPPER, "{bad json"));
  }

  private static GroupMembershipContext group(
      UUID id, String name, com.fasterxml.jackson.databind.JsonNode selector) {
    return new GroupMembershipContext(id, PROJECT_ID, API_ID, name, true, selector, 1L);
  }

  private static com.fasterxml.jackson.databind.JsonNode selector(String... keyValues) {
    try {
      var node = OBJECT_MAPPER.createObjectNode();
      var matchLabels = OBJECT_MAPPER.createObjectNode();
      for (int i = 0; i < keyValues.length; i += 2) {
        matchLabels.put(keyValues[i], keyValues[i + 1]);
      }
      node.set("matchLabels", matchLabels);
      return node;
    } catch (RuntimeException ex) {
      throw ex;
    }
  }
}
