package com.autoapi.gateway.retry;

import static org.junit.jupiter.api.Assertions.*;

import com.autoapi.config.RuntimeRetryPolicyConfig;
import com.autoapi.support.ControllableClock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RetryBudgetRegistryTest {

  private static final UUID API_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID POLICY_ID = UUID.fromString("00000000-0000-0000-0000-000000000020");

  private ControllableClock clock;
  private RetryBudgetRegistry registry;
  private RuntimeRetryPolicyConfig policy;
  private RetryBudgetKey key;

  @BeforeEach
  void setUp() {
    clock = ControllableClock.fixed(Instant.parse("2026-01-01T00:00:00Z"));
    registry = new RetryBudgetRegistry(clock);
    policy =
        new RuntimeRetryPolicyConfig(
            POLICY_ID, 2, 1000, true, true, true, true, List.of("GET"), true, 20, 2, 10);
    key = new RetryBudgetKey(API_ID, "orders-route", POLICY_ID);
  }

  @Test
  void originalRequestsIncreasePercentCapacity() {
    for (int i = 0; i < 100; i++) {
      registry.recordOriginalRequest(key, policy);
    }
    assertTrue(registry.tryConsumeRetry(key, policy));
    var snapshot = registry.snapshot(key, policy);
    assertEquals(20, snapshot.retryCapacity());
    assertEquals(1, snapshot.retriesUsed());
  }

  @Test
  void minimumFloorAllowsRetriesWithoutOriginalRequests() {
    RuntimeRetryPolicyConfig lowFloorPolicy =
        new RuntimeRetryPolicyConfig(
            POLICY_ID, 2, 1000, true, true, true, true, List.of("GET"), true, 0, 1, 2);
    assertTrue(registry.tryConsumeRetry(key, lowFloorPolicy));
    assertTrue(registry.tryConsumeRetry(key, lowFloorPolicy));
    assertFalse(registry.tryConsumeRetry(key, lowFloorPolicy));
  }

  @Test
  void differentRoutesHaveIsolatedBudgets() {
    RetryBudgetKey other = new RetryBudgetKey(API_ID, "other-route", POLICY_ID);
    for (int i = 0; i < 30; i++) {
      registry.tryConsumeRetry(other, policy);
    }
    assertTrue(registry.tryConsumeRetry(key, policy));
  }

  @Test
  void activeBudgetViewsEmptyUntilMatchingRequest() {
    assertTrue(registry.activeBudgetViews().isEmpty());
    registry.recordOriginalRequest(key, policy);
    assertEquals(1, registry.activeBudgetViews().size());
    assertEquals(1, registry.activeBudgetViews().getFirst().originalRequests());
  }

  @Test
  void fixedMinimumFloorCapacityAllowsTwoRetriesThenDeniesThird() {
    RuntimeRetryPolicyConfig fixedFloor =
        new RuntimeRetryPolicyConfig(
            POLICY_ID, 2, 1000, true, true, true, true, List.of("GET"), true, 0, 1, 2);
    registry.recordOriginalRequest(key, fixedFloor);
    assertEquals(2, registry.snapshot(key, fixedFloor).retryCapacity());
    assertTrue(registry.tryConsumeRetry(key, fixedFloor));
    assertTrue(registry.tryConsumeRetry(key, fixedFloor));
    assertFalse(registry.tryConsumeRetry(key, fixedFloor));
    assertEquals(2, registry.snapshot(key, fixedFloor).retriesUsed());
  }

  @Test
  void percentCapacityWithFourOriginalRequestsAllowsTwoRetries() {
    RuntimeRetryPolicyConfig percentPolicy =
        new RuntimeRetryPolicyConfig(
            POLICY_ID, 2, 1000, true, true, true, true, List.of("GET"), true, 50, 0, 30);
    for (int i = 0; i < 4; i++) {
      registry.recordOriginalRequest(key, percentPolicy);
    }
    assertEquals(2, registry.snapshot(key, percentPolicy).retryCapacity());
    assertTrue(registry.tryConsumeRetry(key, percentPolicy));
    assertTrue(registry.tryConsumeRetry(key, percentPolicy));
    assertFalse(registry.tryConsumeRetry(key, percentPolicy));
    assertEquals(2, registry.snapshot(key, percentPolicy).retriesUsed());
  }

  @Test
  void deniedRetryDoesNotIncrementRetriesUsed() {
    RuntimeRetryPolicyConfig fixedFloor =
        new RuntimeRetryPolicyConfig(
            POLICY_ID, 2, 1000, true, true, true, true, List.of("GET"), true, 0, 1, 2);
    registry.recordOriginalRequest(key, fixedFloor);
    assertTrue(registry.tryConsumeRetry(key, fixedFloor));
    assertTrue(registry.tryConsumeRetry(key, fixedFloor));
    assertFalse(registry.tryConsumeRetry(key, fixedFloor));
    assertEquals(2, registry.snapshot(key, fixedFloor).retriesUsed());
  }

  @Test
  void differentPoliciesHaveIsolatedBudgets() {
    UUID otherPolicyId = UUID.fromString("00000000-0000-0000-0000-000000000021");
    RuntimeRetryPolicyConfig otherPolicy =
        new RuntimeRetryPolicyConfig(
            otherPolicyId, 2, 1000, true, true, true, true, List.of("GET"), true, 0, 1, 2);
    RetryBudgetKey otherKey = new RetryBudgetKey(API_ID, "orders-route", otherPolicyId);
    registry.recordOriginalRequest(otherKey, otherPolicy);
    assertTrue(registry.tryConsumeRetry(otherKey, otherPolicy));
    assertTrue(registry.tryConsumeRetry(otherKey, otherPolicy));
    assertFalse(registry.tryConsumeRetry(otherKey, otherPolicy));

    registry.recordOriginalRequest(key, policy);
    assertTrue(registry.tryConsumeRetry(key, policy));
  }

  @Test
  void windowRolloverResetsRetriesUsed() {
    RuntimeRetryPolicyConfig fixedFloor =
        new RuntimeRetryPolicyConfig(
            POLICY_ID, 2, 1000, true, true, true, true, List.of("GET"), true, 0, 1, 2);
    registry.recordOriginalRequest(key, fixedFloor);
    assertTrue(registry.tryConsumeRetry(key, fixedFloor));
    assertEquals(1, registry.snapshot(key, fixedFloor).retriesUsed());

    clock.advance(java.time.Duration.ofSeconds(3));
    registry.recordOriginalRequest(key, fixedFloor);
    assertEquals(0, registry.snapshot(key, fixedFloor).retriesUsed());
    assertTrue(registry.tryConsumeRetry(key, fixedFloor));
  }
}
