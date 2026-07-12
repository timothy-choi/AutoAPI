package com.autoapi.gateway.health;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.autoapi.config.UpstreamTargetReference;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Passive health state is gateway-local; registries do not share ejection state. */
class GatewayLocalHealthIsolationTest {

  private static final UUID API_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID POOL_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
  private static final UUID TARGET_A = UUID.fromString("00000000-0000-0000-0000-000000000020");
  private static final UUID TARGET_B = UUID.fromString("00000000-0000-0000-0000-000000000021");

  @Test
  void ejectionOnOneGatewayRegistryDoesNotAffectAnother() {
    Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    TargetHealthRegistry gatewayA = new TargetHealthRegistry(clock);
    TargetHealthRegistry gatewayB = new TargetHealthRegistry(clock);
    PassiveHealthPolicy policy = new PassiveHealthPolicy(1, Duration.ofSeconds(30), 50);
    TargetKey targetA = new TargetKey(API_ID, POOL_ID, TARGET_A);
    var targets =
        List.of(
            new UpstreamTargetReference(TARGET_A, URI.create("http://upstream-a:8080"), 1),
            new UpstreamTargetReference(TARGET_B, URI.create("http://upstream-b:8080"), 1));

    gatewayA.recordFailure(targetA, FailureCategory.CONNECTION_REFUSED, policy, 2);
    assertTrue(gatewayA.isEjected(targetA));
    assertFalse(gatewayB.isEjected(targetA));

    HealthAwareTargetSelector selectorA = new HealthAwareTargetSelector(gatewayA, clock);
    HealthAwareTargetSelector selectorB = new HealthAwareTargetSelector(gatewayB, clock);

    assertEquals(TARGET_B, selectorA.select(API_ID, POOL_ID, targets, policy).target().targetId());
    assertEquals(TARGET_A, selectorB.select(API_ID, POOL_ID, targets, policy).target().targetId());
  }
}
