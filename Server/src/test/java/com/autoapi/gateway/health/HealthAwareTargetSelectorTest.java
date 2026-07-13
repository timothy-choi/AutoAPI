package com.autoapi.gateway.health;

import static org.junit.jupiter.api.Assertions.*;

import com.autoapi.config.UpstreamTargetReference;
import com.autoapi.support.ControllableClock;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HealthAwareTargetSelectorTest {

  private static final UUID API_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID POOL_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
  private static final UUID TARGET_A = UUID.fromString("00000000-0000-0000-0000-000000000030");
  private static final UUID TARGET_B = UUID.fromString("00000000-0000-0000-0000-000000000031");
  private static final UUID TARGET_C = UUID.fromString("00000000-0000-0000-0000-000000000032");

  private ControllableClock clock;
  private TargetHealthRegistry registry;
  private HealthAwareTargetSelector selector;
  private PassiveHealthPolicy policy;

  @BeforeEach
  void setUp() {
    clock = ControllableClock.fixed(Instant.parse("2026-01-01T00:00:00Z"));
    registry = new TargetHealthRegistry(clock);
    selector = new HealthAwareTargetSelector(registry, clock);
    policy = new PassiveHealthPolicy(1, Duration.ofSeconds(30), 100);
  }

  @Test
  void roundRobinWithoutPolicy() {
    List<UpstreamTargetReference> targets = threeTargets();
    assertEquals(TARGET_A, selector.select(API_ID, POOL_ID, targets, null).target().targetId());
    assertEquals(TARGET_B, selector.select(API_ID, POOL_ID, targets, null).target().targetId());
    assertEquals(TARGET_C, selector.select(API_ID, POOL_ID, targets, null).target().targetId());
    assertEquals(TARGET_A, selector.select(API_ID, POOL_ID, targets, null).target().targetId());
  }

  @Test
  void skipsEjectedTargets() {
    List<UpstreamTargetReference> targets = threeTargets();
    eject(TARGET_B);

    SelectedTarget first = selector.select(API_ID, POOL_ID, targets, policy);
    SelectedTarget second = selector.select(API_ID, POOL_ID, targets, policy);
    SelectedTarget third = selector.select(API_ID, POOL_ID, targets, policy);

    assertFalse(first.forcedSelection());
    assertFalse(second.forcedSelection());
    assertFalse(third.forcedSelection());
    assertNotEquals(TARGET_B, first.target().targetId());
    assertNotEquals(TARGET_B, second.target().targetId());
    assertNotEquals(TARGET_B, third.target().targetId());
  }

  @Test
  void fallsBackWhenAllTargetsEjected() {
    List<UpstreamTargetReference> targets = threeTargets();
    eject(TARGET_A);
    clock.advance(Duration.ofSeconds(5));
    eject(TARGET_B);
    clock.advance(Duration.ofSeconds(10));
    eject(TARGET_C);

    SelectedTarget forced = selector.select(API_ID, POOL_ID, targets, policy);
    assertTrue(forced.forcedSelection());
    assertEquals(TARGET_A, forced.target().targetId());
  }

  @Test
  void counterOverflowStillRotates() {
    List<UpstreamTargetReference> targets = twoTargets();
    for (int i = 0; i < 1_000; i++) {
      selector.select(API_ID, POOL_ID, targets, null);
    }
    SelectedTarget next = selector.select(API_ID, POOL_ID, targets, null);
    assertNotNull(next.target());
  }

  private void eject(UUID targetId) {
    TargetKey key = new TargetKey(API_ID, POOL_ID, targetId);
    registry.recordFailure(key, FailureCategory.CONNECTION_REFUSED, policy, 3);
  }

  private static List<UpstreamTargetReference> threeTargets() {
    return List.of(target(TARGET_A, 8080), target(TARGET_B, 8081), target(TARGET_C, 8082));
  }

  private static List<UpstreamTargetReference> twoTargets() {
    return List.of(target(TARGET_A, 8080), target(TARGET_B, 8081));
  }

  private static UpstreamTargetReference target(UUID id, int port) {
    return new UpstreamTargetReference(id, URI.create("http://127.0.0.1:" + port), 1);
  }
}
