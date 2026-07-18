package com.autoapi.controlplane.validation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.autoapi.controlplane.configversion.CompiledGatewaySection;
import com.autoapi.controlplane.persistence.ApiEntity;
import com.autoapi.controlplane.persistence.RouteEntity;
import com.autoapi.controlplane.persistence.RoutePolicyBindingEntity;
import com.autoapi.controlplane.persistence.TrafficSplitDestinationEntity;
import com.autoapi.controlplane.persistence.TrafficSplitPolicyEntity;
import com.autoapi.controlplane.persistence.UpstreamPoolEntity;
import com.autoapi.controlplane.persistence.UpstreamTargetEntity;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DraftGraphValidatorTrafficSplitTest {

  private static final CompiledGatewaySection GATEWAY = new CompiledGatewaySection("0.0.0.0", 8080);
  private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-01-01T00:00:00Z");
  private static final UUID API_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

  @Test
  void splitRouteWithoutDirectPoolDoesNotRequireRoutePoolNotFound() {
    Graph graph = graph(null);
    ValidationResult result = validate(graph);

    assertFalse(
        result.errors().stream().anyMatch(e -> "ROUTE_POOL_NOT_FOUND".equals(e.code())),
        "Split routes must not require a direct upstream pool on the route");
  }

  @Test
  void rejectsDirectPoolAndTrafficSplitConflict() {
    UUID stablePoolId = UUID.fromString("00000000-0000-0000-0000-000000000010");
    Graph graph = graph(stablePoolId);
    ValidationResult result = validate(graph);

    assertFalse(result.valid());
    assertTrue(
        result.errors().stream().anyMatch(e -> "ROUTE_TRAFFIC_SPLIT_CONFLICT".equals(e.code())));
  }

  private static ValidationResult validate(Graph graph) {
    return DraftGraphValidator.validate(
        graph.api(),
        List.of(graph.route()),
        List.of(graph.stablePool(), graph.canaryPool()),
        List.of(graph.stableTarget(), graph.canaryTarget()),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(graph.policy()),
        graph.destinations(),
        List.of(graph.binding()),
        List.of(),
        List.of(),
        GATEWAY);
  }

  private static Graph graph(UUID routeUpstreamPoolId) {
    UUID stablePoolId = UUID.fromString("00000000-0000-0000-0000-000000000010");
    UUID canaryPoolId = UUID.fromString("00000000-0000-0000-0000-000000000011");
    UUID routeId = UUID.fromString("00000000-0000-0000-0000-000000000020");
    UUID policyId = UUID.fromString("00000000-0000-0000-0000-000000000040");
    UUID stableDestinationId = UUID.fromString("00000000-0000-0000-0000-000000000050");
    UUID canaryDestinationId = UUID.fromString("00000000-0000-0000-0000-000000000051");

    ApiEntity api =
        new ApiEntity(
            API_ID,
            UUID.randomUUID(),
            "orders-api",
            "api.autoapi.local",
            "/",
            true,
            null,
            NOW,
            NOW);
    UpstreamPoolEntity stablePool =
        new UpstreamPoolEntity(stablePoolId, API_ID, "stable", "ROUND_ROBIN", null, NOW, NOW);
    UpstreamPoolEntity canaryPool =
        new UpstreamPoolEntity(canaryPoolId, API_ID, "canary", "ROUND_ROBIN", null, NOW, NOW);
    UpstreamTargetEntity stableTarget =
        new UpstreamTargetEntity(
            UUID.fromString("00000000-0000-0000-0000-000000000030"),
            stablePoolId,
            "http://stable-v1:8080",
            true,
            1,
            NOW,
            NOW);
    UpstreamTargetEntity canaryTarget =
        new UpstreamTargetEntity(
            UUID.fromString("00000000-0000-0000-0000-000000000031"),
            canaryPoolId,
            "http://canary-v1:8080",
            true,
            1,
            NOW,
            NOW);
    RouteEntity route =
        new RouteEntity(
            routeId,
            API_ID,
            "orders-route",
            "api.autoapi.local",
            "/v1/orders",
            new String[] {"GET"},
            routeUpstreamPoolId,
            null,
            true,
            NOW,
            NOW);
    TrafficSplitPolicyEntity policy =
        new TrafficSplitPolicyEntity(
            policyId,
            API_ID,
            "orders-canary",
            "REQUEST_ID",
            null,
            "FALLBACK_TO_PRIMARY",
            true,
            NOW,
            NOW);
    List<TrafficSplitDestinationEntity> destinations =
        List.of(
            new TrafficSplitDestinationEntity(
                stableDestinationId, policyId, stablePoolId, null, "stable", 80, 0, true, NOW, NOW),
            new TrafficSplitDestinationEntity(
                canaryDestinationId,
                policyId,
                canaryPoolId,
                null,
                "canary",
                20,
                1,
                false,
                NOW,
                NOW));
    RoutePolicyBindingEntity binding =
        new RoutePolicyBindingEntity(routeId, false, null, NOW, NOW, null, policyId, null);

    return new Graph(
        API_ID,
        stablePoolId,
        routeId,
        policyId,
        api,
        route,
        stablePool,
        canaryPool,
        stableTarget,
        canaryTarget,
        policy,
        destinations,
        binding);
  }

  private record Graph(
      UUID apiId,
      UUID stablePoolId,
      UUID routeId,
      UUID policyId,
      ApiEntity api,
      RouteEntity route,
      UpstreamPoolEntity stablePool,
      UpstreamPoolEntity canaryPool,
      UpstreamTargetEntity stableTarget,
      UpstreamTargetEntity canaryTarget,
      TrafficSplitPolicyEntity policy,
      List<TrafficSplitDestinationEntity> destinations,
      RoutePolicyBindingEntity binding) {}
}
