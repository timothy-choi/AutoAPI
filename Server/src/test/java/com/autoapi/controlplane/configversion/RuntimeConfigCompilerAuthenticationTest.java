package com.autoapi.controlplane.configversion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.autoapi.controlplane.persistence.ApiKeyEntity;
import com.autoapi.controlplane.persistence.RateLimitPolicyEntity;
import com.autoapi.controlplane.persistence.RouteEntity;
import com.autoapi.controlplane.persistence.RoutePolicyBindingEntity;
import com.autoapi.controlplane.persistence.TrafficSplitDestinationEntity;
import com.autoapi.controlplane.persistence.TrafficSplitPolicyEntity;
import com.autoapi.controlplane.persistence.UpstreamPoolEntity;
import com.autoapi.controlplane.persistence.UpstreamTargetEntity;
import com.autoapi.gateway.config.remote.RemoteSnapshotAdapter;
import com.autoapi.security.ApiKeyDigestService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class RuntimeConfigCompilerAuthenticationTest {

  private static final UUID API_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final CompiledGatewaySection GATEWAY = new CompiledGatewaySection("0.0.0.0", 8080);
  private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-01-01T00:00:00Z");
  private static final String PEPPER = "development-only-test-pepper-minimum-sixteen-characters";

  public static HashableRuntimePayload authenticatedDirectPoolPayload() {
    return compile(directPoolGraph(null));
  }

  public static HashableRuntimePayload authenticatedTrafficSplitPayload() {
    return compile(directPoolGraph(UUID.fromString("00000000-0000-0000-0000-000000000040")));
  }

  @Test
  void compilesAuthenticatedDirectPoolRouteForPhase4StyleBootstrap() {
    Graph graph = directPoolGraph(null);

    CompiledRouteSection route = compile(graph).routes().getFirst();

    assertNotNull(route.authentication());
    assertTrue(route.authentication().required());
    assertNotNull(route.rateLimit());
    assertNull(route.trafficSplit());
    assertNotNull(route.upstreamPool());
  }

  @Test
  void compilesAuthenticatedTrafficSplitRouteWithoutRemovingSecurity() {
    UUID policyId = UUID.fromString("00000000-0000-0000-0000-000000000040");
    Graph graph = directPoolGraph(policyId);

    CompiledRouteSection route = compile(graph).routes().getFirst();

    assertNotNull(route.authentication());
    assertTrue(route.authentication().required());
    assertNotNull(route.rateLimit());
    assertNotNull(route.trafficSplit());
    assertNull(route.upstreamPool());
    assertEquals(2, route.trafficSplit().destinations().size());
  }

  @Test
  void remoteAdapterPreservesAuthenticationForBothRouteModels() {
    Graph direct = directPoolGraph(null);
    HashableRuntimePayload directPayload = compile(direct);
    var directBundle =
        RemoteSnapshotAdapter.toActiveBundle(storedSnapshot(directPayload, 1), API_ID);
    assertTrue(directBundle.runtimeConfig().routes().getFirst().authenticationRequired());
    assertTrue(directBundle.runtimeConfig().routes().getFirst().rateLimitEnabled());
    assertEquals(false, directBundle.runtimeConfig().routes().getFirst().trafficSplitEnabled());

    Graph split = directPoolGraph(UUID.fromString("00000000-0000-0000-0000-000000000040"));
    HashableRuntimePayload splitPayload = compile(split);
    var splitBundle = RemoteSnapshotAdapter.toActiveBundle(storedSnapshot(splitPayload, 2), API_ID);
    assertTrue(splitBundle.runtimeConfig().routes().getFirst().authenticationRequired());
    assertTrue(splitBundle.runtimeConfig().routes().getFirst().rateLimitEnabled());
    assertTrue(splitBundle.runtimeConfig().routes().getFirst().trafficSplitEnabled());
  }

  private static HashableRuntimePayload compile(Graph graph) {
    RoutePolicyBindingEntity binding =
        new RoutePolicyBindingEntity(
            graph.routeId(),
            true,
            graph.rateLimitPolicyId(),
            NOW,
            NOW,
            null,
            graph.trafficSplitPolicyId());
    RouteEntity publishedRoute =
        graph.trafficSplitPolicyId() == null
            ? graph.route()
            : new RouteEntity(
                graph.routeId(),
                API_ID,
                graph.route().name(),
                graph.route().host(),
                graph.route().pathPrefix(),
                graph.route().methods(),
                null,
                true,
                NOW,
                NOW);
    return RuntimeConfigCompiler.compile(
        API_ID,
        GATEWAY,
        List.of(publishedRoute),
        Map.of(graph.stablePoolId(), graph.stablePool(), graph.canaryPoolId(), graph.canaryPool()),
        Map.of(
            graph.stablePoolId(), List.of(graph.stableTarget()),
            graph.canaryPoolId(), List.of(graph.canaryTarget())),
        Map.of(graph.routeId(), binding),
        Map.of(graph.rateLimitPolicyId(), graph.rateLimitPolicy()),
        Map.of(),
        Map.of(),
        graph.trafficSplitPolicyId() == null
            ? Map.of()
            : Map.of(graph.trafficSplitPolicyId(), graph.trafficSplitPolicy()),
        graph.trafficSplitPolicyId() == null
            ? Map.of()
            : Map.of(graph.trafficSplitPolicyId(), graph.destinations()),
        List.of(graph.apiKey()),
        NOW);
  }

  private static StoredRuntimeSnapshot storedSnapshot(
      HashableRuntimePayload payload, long version) {
    String hash = RuntimeContentHasher.sha256Hex(RuntimeContentHasher.canonicalJson(payload));
    return RuntimeConfigCompiler.toStoredSnapshot(payload, version, hash);
  }

  private static Graph directPoolGraph(UUID trafficSplitPolicyId) {
    UUID stablePoolId = UUID.fromString("00000000-0000-0000-0000-000000000010");
    UUID canaryPoolId = UUID.fromString("00000000-0000-0000-0000-000000000011");
    UUID routeId = UUID.fromString("00000000-0000-0000-0000-000000000020");
    UUID rateLimitPolicyId = UUID.fromString("00000000-0000-0000-0000-000000000030");
    UUID policyId =
        trafficSplitPolicyId == null
            ? null
            : UUID.fromString("00000000-0000-0000-0000-000000000040");

    UpstreamPoolEntity stablePool =
        new UpstreamPoolEntity(stablePoolId, API_ID, "stable", "ROUND_ROBIN", null, NOW, NOW);
    UpstreamPoolEntity canaryPool =
        new UpstreamPoolEntity(canaryPoolId, API_ID, "canary", "ROUND_ROBIN", null, NOW, NOW);
    UpstreamTargetEntity stableTarget =
        new UpstreamTargetEntity(
            UUID.fromString("00000000-0000-0000-0000-000000000031"),
            stablePoolId,
            "http://stable-v1:8080",
            true,
            1,
            NOW,
            NOW);
    UpstreamTargetEntity canaryTarget =
        new UpstreamTargetEntity(
            UUID.fromString("00000000-0000-0000-0000-000000000032"),
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
            new String[] {"GET", "POST"},
            stablePoolId,
            true,
            NOW,
            NOW);
    RateLimitPolicyEntity rateLimitPolicy =
        new RateLimitPolicyEntity(
            rateLimitPolicyId,
            API_ID,
            "phase4-smoke-limit",
            5,
            300,
            "API_KEY",
            "FAIL_OPEN",
            true,
            NOW,
            NOW);
    ApiKeyEntity apiKey =
        new ApiKeyEntity(
            UUID.fromString("00000000-0000-0000-0000-000000000050"),
            API_ID,
            "abc123",
            "phase4-smoke-auth-client",
            "ak_live_abc123",
            ApiKeyDigestService.digestSecret("secret-for-test-key-material", PEPPER),
            true,
            null,
            NOW,
            NOW,
            null);
    TrafficSplitPolicyEntity splitPolicy =
        policyId == null
            ? null
            : new TrafficSplitPolicyEntity(
                policyId, API_ID, "orders-canary", "REQUEST_ID", null, "STRICT", true, NOW, NOW);
    List<TrafficSplitDestinationEntity> destinations =
        policyId == null
            ? List.of()
            : List.of(
                new TrafficSplitDestinationEntity(
                    UUID.fromString("00000000-0000-0000-0000-000000000051"),
                    policyId,
                    stablePoolId,
                    "stable",
                    80,
                    0,
                    true,
                    NOW,
                    NOW),
                new TrafficSplitDestinationEntity(
                    UUID.fromString("00000000-0000-0000-0000-000000000052"),
                    policyId,
                    canaryPoolId,
                    "canary",
                    20,
                    1,
                    false,
                    NOW,
                    NOW));
    return new Graph(
        routeId,
        stablePoolId,
        canaryPoolId,
        rateLimitPolicyId,
        policyId,
        route,
        stablePool,
        canaryPool,
        stableTarget,
        canaryTarget,
        rateLimitPolicy,
        apiKey,
        splitPolicy,
        destinations);
  }

  private record Graph(
      UUID routeId,
      UUID stablePoolId,
      UUID canaryPoolId,
      UUID rateLimitPolicyId,
      UUID trafficSplitPolicyId,
      RouteEntity route,
      UpstreamPoolEntity stablePool,
      UpstreamPoolEntity canaryPool,
      UpstreamTargetEntity stableTarget,
      UpstreamTargetEntity canaryTarget,
      RateLimitPolicyEntity rateLimitPolicy,
      ApiKeyEntity apiKey,
      TrafficSplitPolicyEntity trafficSplitPolicy,
      List<TrafficSplitDestinationEntity> destinations) {}
}
