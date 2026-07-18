package com.autoapi.controlplane.configversion;

import static org.junit.jupiter.api.Assertions.*;

import com.autoapi.controlplane.persistence.BackendHealthPolicyEntity;
import com.autoapi.controlplane.persistence.RouteEntity;
import com.autoapi.controlplane.persistence.UpstreamPoolEntity;
import com.autoapi.controlplane.persistence.UpstreamTargetEntity;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RuntimeConfigCompilerHealthTest {

  private static final UUID API_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final CompiledGatewaySection GATEWAY = new CompiledGatewaySection("0.0.0.0", 8080);
  private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-01-01T00:00:00Z");

  @Test
  void compilesEnabledHealthPolicyIntoSnapshot() {
    Graph graph = baseGraph(null);
    UUID policyId = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    BackendHealthPolicyEntity policy =
        new BackendHealthPolicyEntity(policyId, API_ID, "passive", 3, 45, 50, true, NOW, NOW);
    UpstreamPoolEntity poolWithPolicy =
        new UpstreamPoolEntity(
            graph.poolId(), API_ID, "orders-v1", "ROUND_ROBIN", policyId, NOW, NOW);

    HashableRuntimePayload payload =
        compile(graph.route(), poolWithPolicy, graph.target(), Map.of(policyId, policy));

    CompiledBackendHealthSection health =
        payload.routes().getFirst().upstreamPool().backendHealth();
    assertNotNull(health);
    assertEquals(3, health.consecutiveFailureThreshold());
    assertEquals(45, health.ejectionDurationSeconds());
    assertEquals(50, health.maxEjectionPercent());
  }

  @Test
  void omitsDisabledOrMissingPolicyFromSnapshot() {
    Graph graph = baseGraph(null);
    UUID policyId = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    BackendHealthPolicyEntity disabledPolicy =
        new BackendHealthPolicyEntity(policyId, API_ID, "disabled", 3, 45, 50, false, NOW, NOW);
    UpstreamPoolEntity poolWithDisabledPolicy =
        new UpstreamPoolEntity(
            graph.poolId(), API_ID, "orders-v1", "ROUND_ROBIN", policyId, NOW, NOW);

    HashableRuntimePayload disabledPayload =
        compile(
            graph.route(),
            poolWithDisabledPolicy,
            graph.target(),
            Map.of(policyId, disabledPolicy));
    assertNull(disabledPayload.routes().getFirst().upstreamPool().backendHealth());

    HashableRuntimePayload unboundPayload =
        compile(graph.route(), graph.pool(), graph.target(), Map.of());
    assertNull(unboundPayload.routes().getFirst().upstreamPool().backendHealth());
  }

  @Test
  void healthPolicyChangeProducesDifferentHash() {
    Graph graph = baseGraph(null);
    UUID policyId = UUID.fromString("00000000-0000-0000-0000-0000000000a3");
    BackendHealthPolicyEntity original =
        new BackendHealthPolicyEntity(policyId, API_ID, "passive", 2, 30, 50, true, NOW, NOW);
    BackendHealthPolicyEntity updated =
        new BackendHealthPolicyEntity(policyId, API_ID, "passive", 5, 30, 50, true, NOW, NOW);
    UpstreamPoolEntity boundPool =
        new UpstreamPoolEntity(
            graph.poolId(), API_ID, "orders-v1", "ROUND_ROBIN", policyId, NOW, NOW);

    String originalHash =
        hash(compile(graph.route(), boundPool, graph.target(), Map.of(policyId, original)));
    String updatedHash =
        hash(compile(graph.route(), boundPool, graph.target(), Map.of(policyId, updated)));
    assertNotEquals(originalHash, updatedHash);
  }

  @Test
  void sameHealthGraphProducesSameHash() {
    Graph graph = baseGraph(null);
    UUID policyId = UUID.fromString("00000000-0000-0000-0000-0000000000a4");
    BackendHealthPolicyEntity policy =
        new BackendHealthPolicyEntity(policyId, API_ID, "passive", 2, 30, 50, true, NOW, NOW);
    UpstreamPoolEntity boundPool =
        new UpstreamPoolEntity(
            graph.poolId(), API_ID, "orders-v1", "ROUND_ROBIN", policyId, NOW, NOW);
    Map<UUID, BackendHealthPolicyEntity> policies = Map.of(policyId, policy);

    HashableRuntimePayload first = compile(graph.route(), boundPool, graph.target(), policies);
    HashableRuntimePayload second = compile(graph.route(), boundPool, graph.target(), policies);

    assertEquals(hash(first), hash(second));
  }

  private static HashableRuntimePayload compile(
      RouteEntity route,
      UpstreamPoolEntity pool,
      UpstreamTargetEntity target,
      Map<UUID, BackendHealthPolicyEntity> healthPolicies) {
    return RuntimeConfigCompiler.compile(
        API_ID,
        GATEWAY,
        List.of(route),
        Map.of(pool.id(), pool),
        Map.of(pool.id(), List.of(target)),
        Map.of(),
        Map.of(),
        healthPolicies,
        Map.of(),
        Map.of(),
        Map.of(),
        Map.of(),
        Map.of(),
        Map.of(),
        List.of(),
        NOW);
  }

  private static String hash(HashableRuntimePayload payload) {
    return RuntimeContentHasher.sha256Hex(RuntimeContentHasher.canonicalJson(payload));
  }

  private static Graph baseGraph(UUID backendHealthPolicyId) {
    UUID poolId = UUID.fromString("00000000-0000-0000-0000-000000000010");
    UUID routeId = UUID.fromString("00000000-0000-0000-0000-000000000020");
    UUID targetId = UUID.fromString("00000000-0000-0000-0000-000000000030");
    UpstreamPoolEntity pool =
        new UpstreamPoolEntity(
            poolId, API_ID, "orders-v1", "ROUND_ROBIN", backendHealthPolicyId, NOW, NOW);
    UpstreamTargetEntity target =
        new UpstreamTargetEntity(targetId, poolId, "http://upstream-v1:8080", true, 1, NOW, NOW);
    RouteEntity route =
        new RouteEntity(
            routeId,
            API_ID,
            "orders-route",
            "api.autoapi.local",
            "/v1/orders",
            new String[] {"GET"},
            poolId,
            null,
            true,
            NOW,
            NOW);
    return new Graph(poolId, route, pool, target);
  }

  private record Graph(
      UUID poolId, RouteEntity route, UpstreamPoolEntity pool, UpstreamTargetEntity target) {}
}
