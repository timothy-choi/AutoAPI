package com.autoapi.controlplane.configversion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.autoapi.controlplane.persistence.RouteEntity;
import com.autoapi.controlplane.persistence.RoutePolicyBindingEntity;
import com.autoapi.controlplane.persistence.TrafficSplitDestinationEntity;
import com.autoapi.controlplane.persistence.TrafficSplitPolicyEntity;
import com.autoapi.controlplane.persistence.UpstreamPoolEntity;
import com.autoapi.controlplane.persistence.UpstreamTargetEntity;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RuntimeConfigCompilerTrafficSplitTest {

  private static final UUID API_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final CompiledGatewaySection GATEWAY = new CompiledGatewaySection("0.0.0.0", 8080);
  private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-01-01T00:00:00Z");

  @Test
  void compilesTrafficSplitAndOmitsDirectPool() {
    Graph graph = graph();
    HashableRuntimePayload payload = compile(graph);
    CompiledRouteSection route = payload.routes().getFirst();
    assertNotNull(route.trafficSplit());
    assertNull(route.upstreamPool());
    assertEquals(2, route.trafficSplit().destinations().size());
    assertEquals("stable", route.trafficSplit().destinations().getFirst().name());
  }

  @Test
  void weightChangeAltersContentHash() {
    Graph graph = graph();
    String original = hash(compile(graph));
    TrafficSplitDestinationEntity updatedCanary =
        new TrafficSplitDestinationEntity(
            graph.canaryDestinationId(),
            graph.policyId(),
            graph.canaryPoolId(),
            null,
            "canary",
            15,
            1,
            false,
            NOW,
            NOW);
    Graph updated =
        new Graph(
            graph.routeId(),
            graph.policyId(),
            graph.stablePoolId(),
            graph.canaryPoolId(),
            graph.stableDestinationId(),
            graph.canaryDestinationId(),
            graph.route(),
            graph.stablePool(),
            graph.canaryPool(),
            graph.stableTarget(),
            graph.canaryTarget(),
            List.of(graph.stableDestination(), updatedCanary));
    assertNotEquals(original, hash(compile(updated)));
  }

  @Test
  void trafficSplitFingerprintIncludedInSnapshot() {
    Graph graph = graph();
    CompiledTrafficSplitSection split = compile(graph).routes().getFirst().trafficSplit();
    assertNotNull(split.fingerprint());
    assertEquals("HEADER", split.selectionKey());
    assertEquals("FALLBACK_TO_PRIMARY", split.fallbackMode());
  }

  private static HashableRuntimePayload compile(Graph graph) {
    RoutePolicyBindingEntity binding =
        new RoutePolicyBindingEntity(
            graph.routeId(), false, null, NOW, NOW, null, graph.policyId(), null);
    RouteEntity splitRoute =
        new RouteEntity(
            graph.routeId(),
            API_ID,
            "orders-route",
            "api.autoapi.local",
            "/v1/orders",
            new String[] {"GET"},
            null,
            null,
            true,
            NOW,
            NOW);
    return RuntimeConfigCompiler.compile(
        API_ID,
        GATEWAY,
        List.of(splitRoute),
        Map.of(graph.stablePoolId(), graph.stablePool(), graph.canaryPoolId(), graph.canaryPool()),
        Map.of(
            graph.stablePoolId(), List.of(graph.stableTarget()),
            graph.canaryPoolId(), List.of(graph.canaryTarget())),
        Map.of(graph.routeId(), binding),
        Map.of(),
        Map.of(),
        Map.of(),
        Map.of(),
        Map.of(graph.policyId(), graph.policy()),
        Map.of(graph.policyId(), graph.destinations()),
        Map.of(),
        Map.of(),
        List.of(),
        NOW);
  }

  private static String hash(HashableRuntimePayload payload) {
    return RuntimeContentHasher.sha256Hex(RuntimeContentHasher.canonicalJson(payload));
  }

  private static Graph graph() {
    UUID stablePoolId = UUID.fromString("00000000-0000-0000-0000-000000000010");
    UUID canaryPoolId = UUID.fromString("00000000-0000-0000-0000-000000000011");
    UUID routeId = UUID.fromString("00000000-0000-0000-0000-000000000020");
    UUID stableTargetId = UUID.fromString("00000000-0000-0000-0000-000000000030");
    UUID canaryTargetId = UUID.fromString("00000000-0000-0000-0000-000000000031");
    UUID policyId = UUID.fromString("00000000-0000-0000-0000-000000000040");
    UUID stableDestinationId = UUID.fromString("00000000-0000-0000-0000-000000000050");
    UUID canaryDestinationId = UUID.fromString("00000000-0000-0000-0000-000000000051");

    UpstreamPoolEntity stablePool =
        new UpstreamPoolEntity(stablePoolId, API_ID, "stable", "ROUND_ROBIN", null, NOW, NOW);
    UpstreamPoolEntity canaryPool =
        new UpstreamPoolEntity(canaryPoolId, API_ID, "canary", "ROUND_ROBIN", null, NOW, NOW);
    UpstreamTargetEntity stableTarget =
        new UpstreamTargetEntity(
            stableTargetId, stablePoolId, "http://stable-v1:8080", true, 1, NOW, NOW);
    UpstreamTargetEntity canaryTarget =
        new UpstreamTargetEntity(
            canaryTargetId, canaryPoolId, "http://canary-v1:8080", true, 1, NOW, NOW);
    RouteEntity route =
        new RouteEntity(
            routeId,
            API_ID,
            "orders-route",
            "api.autoapi.local",
            "/v1/orders",
            new String[] {"GET"},
            null,
            null,
            true,
            NOW,
            NOW);
    TrafficSplitPolicyEntity policy =
        new TrafficSplitPolicyEntity(
            policyId,
            API_ID,
            "orders-canary",
            "HEADER",
            "X-AutoAPI-Test-User",
            "FALLBACK_TO_PRIMARY",
            true,
            NOW,
            NOW);
    TrafficSplitDestinationEntity stableDestination =
        new TrafficSplitDestinationEntity(
            stableDestinationId, policyId, stablePoolId, null, "stable", 80, 0, true, NOW, NOW);
    TrafficSplitDestinationEntity canaryDestination =
        new TrafficSplitDestinationEntity(
            canaryDestinationId, policyId, canaryPoolId, null, "canary", 20, 1, false, NOW, NOW);
    return new Graph(
        routeId,
        policyId,
        stablePoolId,
        canaryPoolId,
        stableDestinationId,
        canaryDestinationId,
        route,
        stablePool,
        canaryPool,
        stableTarget,
        canaryTarget,
        List.of(stableDestination, canaryDestination));
  }

  private record Graph(
      UUID routeId,
      UUID policyId,
      UUID stablePoolId,
      UUID canaryPoolId,
      UUID stableDestinationId,
      UUID canaryDestinationId,
      RouteEntity route,
      UpstreamPoolEntity stablePool,
      UpstreamPoolEntity canaryPool,
      UpstreamTargetEntity stableTarget,
      UpstreamTargetEntity canaryTarget,
      List<TrafficSplitDestinationEntity> destinations) {

    TrafficSplitPolicyEntity policy() {
      return new TrafficSplitPolicyEntity(
          policyId,
          API_ID,
          "orders-canary",
          "HEADER",
          "X-AutoAPI-Test-User",
          "FALLBACK_TO_PRIMARY",
          true,
          NOW,
          NOW);
    }

    TrafficSplitDestinationEntity stableDestination() {
      return destinations.getFirst();
    }
  }
}
