package com.autoapi.controlplane.configversion;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoapi.controlplane.persistence.CircuitBreakerPolicyEntity;
import com.autoapi.controlplane.persistence.RouteEntity;
import com.autoapi.controlplane.persistence.RoutePolicyBindingEntity;
import com.autoapi.controlplane.persistence.UpstreamPoolEntity;
import com.autoapi.controlplane.persistence.UpstreamTargetEntity;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RuntimeConfigCompilerCircuitBreakerTest {

  private static final UUID API_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-01-01T00:00:00Z");

  @Test
  void compilesEnabledCircuitBreakerBinding() {
    UUID routeId = UUID.fromString("00000000-0000-0000-0000-000000000020");
    UUID poolId = UUID.fromString("00000000-0000-0000-0000-000000000010");
    UUID targetId = UUID.fromString("00000000-0000-0000-0000-000000000030");
    UUID policyId = UUID.fromString("00000000-0000-0000-0000-0000000000c1");

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
    UpstreamPoolEntity pool =
        new UpstreamPoolEntity(poolId, API_ID, "primary", "ROUND_ROBIN", null, NOW, NOW);
    UpstreamTargetEntity target =
        new UpstreamTargetEntity(targetId, poolId, "http://upstream-v1:8080", true, 1, NOW, NOW);
    RoutePolicyBindingEntity binding =
        new RoutePolicyBindingEntity(routeId, false, null, NOW, NOW, null, null, policyId);
    CircuitBreakerPolicyEntity policy =
        new CircuitBreakerPolicyEntity(
            policyId, API_ID, "breaker", 3, 30, 10, 2, 1, true, true, true, true, true, true, false,
            true, NOW, NOW);

    HashableRuntimePayload payload =
        RuntimeConfigCompiler.compile(
            API_ID,
            new CompiledGatewaySection("0.0.0.0", 8080),
            List.of(route),
            Map.of(poolId, pool),
            Map.of(poolId, List.of(target)),
            Map.of(routeId, binding),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(policyId, policy),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            List.of(),
            NOW);

    CompiledCircuitBreakerSection compiled = payload.routes().getFirst().circuitBreaker();
    assertThat(compiled).isNotNull();
    assertThat(compiled.policyId()).isEqualTo(policyId);
    assertThat(compiled.failureThreshold()).isEqualTo(3);
    assertThat(compiled.failurePredicate().countHttp5xx()).isTrue();
  }
}
