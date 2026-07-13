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
}
