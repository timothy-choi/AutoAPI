package com.autoapi.controlplane.configversion;

import static org.junit.jupiter.api.Assertions.*;

import com.autoapi.controlplane.persistence.RetryPolicyEntity;
import com.autoapi.controlplane.persistence.RouteEntity;
import com.autoapi.controlplane.persistence.RoutePolicyBindingEntity;
import com.autoapi.controlplane.persistence.UpstreamPoolEntity;
import com.autoapi.controlplane.persistence.UpstreamTargetEntity;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RuntimeConfigCompilerRetryTest {

  private static final UUID API_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final CompiledGatewaySection GATEWAY = new CompiledGatewaySection("0.0.0.0", 8080);
  private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-01-01T00:00:00Z");

  @Test
  void compilesEnabledRetryPolicyIntoSnapshot() {
    Graph graph = baseGraph();
    UUID policyId = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    RetryPolicyEntity policy = policy(policyId, 2, 1000, true);
    RoutePolicyBindingEntity binding =
        new RoutePolicyBindingEntity(graph.routeId(), false, null, NOW, NOW, policyId, null, null);

    HashableRuntimePayload payload = compile(graph, binding, Map.of(policyId, policy));

    CompiledRetrySection retry = payload.routes().getFirst().retry();
    assertNotNull(retry);
    assertEquals(2, retry.maxAttempts());
    assertEquals(List.of("GET", "HEAD"), retry.retryableMethods());
  }

  @Test
  void omitsDisabledOrMissingPolicyFromSnapshot() {
    Graph graph = baseGraph();
    UUID policyId = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    RetryPolicyEntity disabled = policy(policyId, 2, 1000, false);
    RoutePolicyBindingEntity binding =
        new RoutePolicyBindingEntity(graph.routeId(), false, null, NOW, NOW, policyId, null, null);

    HashableRuntimePayload disabledPayload = compile(graph, binding, Map.of(policyId, disabled));
    assertNull(disabledPayload.routes().getFirst().retry());

    HashableRuntimePayload unboundPayload = compile(graph, null, Map.of());
    assertNull(unboundPayload.routes().getFirst().retry());
  }

  @Test
  void retryPolicyChangeProducesDifferentHash() {
    Graph graph = baseGraph();
    UUID policyId = UUID.fromString("00000000-0000-0000-0000-0000000000b3");
    RetryPolicyEntity original = policy(policyId, 2, 1000, true);
    RetryPolicyEntity updated = policy(policyId, 3, 1000, true);
    RoutePolicyBindingEntity binding =
        new RoutePolicyBindingEntity(graph.routeId(), false, null, NOW, NOW, policyId, null, null);

    String originalHash = hash(compile(graph, binding, Map.of(policyId, original)));
    String updatedHash = hash(compile(graph, binding, Map.of(policyId, updated)));
    assertNotEquals(originalHash, updatedHash);
  }

  @Test
  void noRetryPolicyMeansOneAttemptOnly() {
    Graph graph = baseGraph();
    HashableRuntimePayload payload = compile(graph, null, Map.of());
    assertNull(payload.routes().getFirst().retry());
  }

  private static HashableRuntimePayload compile(
      Graph graph, RoutePolicyBindingEntity binding, Map<UUID, RetryPolicyEntity> retryPolicies) {
    Map<UUID, RoutePolicyBindingEntity> bindings =
        binding == null ? Map.of() : Map.of(binding.routeId(), binding);
    return RuntimeConfigCompiler.compile(
        API_ID,
        GATEWAY,
        List.of(graph.route()),
        Map.of(graph.poolId(), graph.pool()),
        Map.of(graph.poolId(), List.of(graph.target())),
        bindings,
        Map.of(),
        Map.of(),
        retryPolicies,
        Map.of(),
        Map.of(),
        Map.of(),
        List.of(),
        NOW);
  }

  private static String hash(HashableRuntimePayload payload) {
    return RuntimeContentHasher.sha256Hex(RuntimeContentHasher.canonicalJson(payload));
  }

  private static RetryPolicyEntity policy(
      UUID id, int maxAttempts, int timeoutMs, boolean enabled) {
    return new RetryPolicyEntity(
        id,
        API_ID,
        "retry",
        maxAttempts,
        timeoutMs,
        true,
        true,
        true,
        true,
        new String[] {"HEAD", "GET"},
        true,
        20,
        2,
        10,
        enabled,
        NOW,
        NOW);
  }

  private static Graph baseGraph() {
    UUID poolId = UUID.fromString("00000000-0000-0000-0000-000000000010");
    UUID routeId = UUID.fromString("00000000-0000-0000-0000-000000000020");
    UUID targetId = UUID.fromString("00000000-0000-0000-0000-000000000030");
    UpstreamPoolEntity pool =
        new UpstreamPoolEntity(poolId, API_ID, "orders-v1", "ROUND_ROBIN", null, NOW, NOW);
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
            true,
            NOW,
            NOW);
    return new Graph(poolId, routeId, route, pool, target);
  }

  private record Graph(
      UUID poolId,
      UUID routeId,
      RouteEntity route,
      UpstreamPoolEntity pool,
      UpstreamTargetEntity target) {}
}
