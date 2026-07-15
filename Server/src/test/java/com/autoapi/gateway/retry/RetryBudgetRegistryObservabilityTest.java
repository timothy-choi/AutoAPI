package com.autoapi.gateway.retry;

import static org.junit.jupiter.api.Assertions.*;

import com.autoapi.config.RuntimeRetryPolicyConfig;
import com.autoapi.support.ControllableClock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RetryBudgetRegistryObservabilityTest {

  private static final UUID API_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID POLICY_ID = UUID.fromString("00000000-0000-0000-0000-000000000020");

  private RetryBudgetRegistry registry;
  private RuntimeRetryPolicyConfig policy;
  private RetryBudgetKey key;

  @BeforeEach
  void setUp() {
    registry =
        new RetryBudgetRegistry(ControllableClock.fixed(Instant.parse("2026-01-01T00:00:00Z")));
    policy =
        new RuntimeRetryPolicyConfig(
            POLICY_ID, 2, 1000, true, true, true, true, List.of("GET"), true, 50, 10, 10);
    key = new RetryBudgetKey(API_ID, "orders-route", POLICY_ID);
  }

  @Test
  void directFirstAttemptSuccessIncrementsOnlyOriginalRequests() {
    registry.recordOriginalRequest(key, policy);

    var snapshot = registry.snapshot(key, policy);
    assertEquals(1, snapshot.originalRequests());
    assertEquals(0, snapshot.retriesUsed());
    assertEquals(0, snapshot.retryAttempts());
    assertEquals(0, snapshot.retrySuccesses());
    assertEquals(0, snapshot.retryFailures());
  }

  @Test
  void firstFailureSecondSuccessIncrementsRetryCounters() {
    registry.recordOriginalRequest(key, policy);
    assertTrue(registry.tryConsumeRetry(key, policy));
    registry.recordRetryAttempt(key, policy);
    registry.recordRetrySuccess(key, policy);

    var snapshot = registry.snapshot(key, policy);
    assertEquals(1, snapshot.originalRequests());
    assertEquals(1, snapshot.retriesUsed());
    assertEquals(1, snapshot.retryAttempts());
    assertEquals(1, snapshot.retrySuccesses());
    assertEquals(0, snapshot.retryFailures());
  }

  @Test
  void bothAttemptsFailIncrementsRetryFailure() {
    registry.recordOriginalRequest(key, policy);
    assertTrue(registry.tryConsumeRetry(key, policy));
    registry.recordRetryAttempt(key, policy);
    registry.recordRetryFailure(key, policy);

    var snapshot = registry.snapshot(key, policy);
    assertEquals(1, snapshot.retryAttempts());
    assertEquals(0, snapshot.retrySuccesses());
    assertEquals(1, snapshot.retryFailures());
  }

  @Test
  void budgetDenialIncrementsBudgetDenialsWithoutRetryAttempt() {
    RuntimeRetryPolicyConfig tight =
        new RuntimeRetryPolicyConfig(
            POLICY_ID, 2, 1000, true, true, true, true, List.of("GET"), true, 0, 0, 10);
    registry.recordBudgetDenial(key, tight);

    var snapshot = registry.snapshot(key, tight);
    assertEquals(1, snapshot.budgetDenials());
    assertEquals(0, snapshot.retryAttempts());
  }

  @Test
  void countersIsolatedByRoutePolicy() {
    RetryBudgetKey other = new RetryBudgetKey(API_ID, "other-route", POLICY_ID);
    registry.recordOriginalRequest(key, policy);
    registry.recordRetryAttempt(key, policy);

    var otherSnapshot = registry.snapshot(other, policy);
    assertEquals(0, otherSnapshot.retryAttempts());
    assertEquals(0, otherSnapshot.originalRequests());
  }

  @Test
  void activeBudgetViewsExposeRetryCountersWithoutSensitiveData() {
    registry.recordOriginalRequest(key, policy);
    registry.recordRetryAttempt(key, policy);
    registry.recordRetrySuccess(key, policy);

    RetryBudgetRegistry.BudgetView view = registry.activeBudgetViews().getFirst();
    assertEquals(API_ID, view.apiId());
    assertEquals("orders-route", view.routeId());
    assertEquals(POLICY_ID, view.policyId());
    assertEquals(1, view.retryAttempts());
    assertEquals(1, view.retrySuccesses());
    assertEquals(0, view.retriesUsed());
  }
}
