package com.autoapi.gateway.health;

import static org.junit.jupiter.api.Assertions.*;

import com.autoapi.support.ControllableClock;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TargetHealthRegistryTest {

  private static final UUID API_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID POOL_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
  private static final UUID TARGET_A = UUID.fromString("00000000-0000-0000-0000-000000000030");
  private static final UUID TARGET_B = UUID.fromString("00000000-0000-0000-0000-000000000031");
  private static final UUID TARGET_C = UUID.fromString("00000000-0000-0000-0000-000000000032");
  private static final UUID TARGET_D = UUID.fromString("00000000-0000-0000-0000-000000000033");

  private ControllableClock clock;
  private TargetHealthRegistry registry;
  private PassiveHealthPolicy policy;

  @BeforeEach
  void setUp() {
    clock = ControllableClock.fixed(Instant.parse("2026-01-01T00:00:00Z"));
    registry = new TargetHealthRegistry(clock);
    policy = new PassiveHealthPolicy(2, Duration.ofSeconds(30), 50);
  }

  @Test
  void countsConsecutiveFailuresBeforeEjection() {
    TargetKey key = new TargetKey(API_ID, POOL_ID, TARGET_A);

    registry.recordFailure(key, FailureCategory.CONNECTION_REFUSED, policy, 2);
    assertEquals(1, registry.getState(key).consecutiveQualifyingFailures());
    assertFalse(registry.isEjected(key));

    registry.recordFailure(key, FailureCategory.CONNECTION_REFUSED, policy, 2);
    assertTrue(registry.isEjected(key));
    assertEquals(0, registry.getState(key).consecutiveQualifyingFailures());
  }

  @Test
  void ejectionExpiresWhenClockAdvances() {
    TargetKey key = new TargetKey(API_ID, POOL_ID, TARGET_A);
    registry.recordFailure(key, FailureCategory.CONNECTION_REFUSED, policy, 2);
    registry.recordFailure(key, FailureCategory.CONNECTION_REFUSED, policy, 2);
    assertTrue(registry.isEjected(key));

    clock.advance(Duration.ofSeconds(31));
    assertFalse(registry.isEjected(key));
  }

  @Test
  void successClearsFailureStateAndEjection() {
    TargetKey key = new TargetKey(API_ID, POOL_ID, TARGET_A);
    registry.recordFailure(key, FailureCategory.CONNECTION_REFUSED, policy, 2);
    registry.recordFailure(key, FailureCategory.CONNECTION_REFUSED, policy, 2);
    assertTrue(registry.isEjected(key));

    registry.recordSuccess(key);
    TargetHealthState state = registry.getState(key);
    assertFalse(registry.isEjected(key));
    assertEquals(0, state.consecutiveQualifyingFailures());
    assertNull(state.ejectedUntil());
  }

  @Test
  void respectsMaxEjectionPercent() {
    PassiveHealthPolicy maxOneOfFour = new PassiveHealthPolicy(1, Duration.ofSeconds(30), 50);
    TargetKey a = new TargetKey(API_ID, POOL_ID, TARGET_A);
    TargetKey b = new TargetKey(API_ID, POOL_ID, TARGET_B);
    TargetKey c = new TargetKey(API_ID, POOL_ID, TARGET_C);
    TargetKey d = new TargetKey(API_ID, POOL_ID, TARGET_D);

    registry.recordFailure(a, FailureCategory.CONNECTION_REFUSED, maxOneOfFour, 4);
    registry.recordFailure(b, FailureCategory.CONNECTION_REFUSED, maxOneOfFour, 4);
    assertTrue(registry.isEjected(a));
    assertTrue(registry.isEjected(b));
    assertEquals(2, registry.countActiveEjections(API_ID, POOL_ID, clock.instant()));

    registry.recordFailure(c, FailureCategory.CONNECTION_REFUSED, maxOneOfFour, 4);
    assertFalse(registry.isEjected(c));
    assertEquals(2, registry.countActiveEjections(API_ID, POOL_ID, clock.instant()));
  }

  @Test
  void concurrentFailureRecordingIsSafe() throws Exception {
    TargetKey key = new TargetKey(API_ID, POOL_ID, TARGET_A);
    PassiveHealthPolicy permissive = new PassiveHealthPolicy(100, Duration.ofSeconds(30), 100);
    int workers = 32;
    ExecutorService executor = Executors.newFixedThreadPool(workers);
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(workers);

    for (int i = 0; i < workers; i++) {
      executor.submit(
          () -> {
            try {
              start.await();
              registry.recordFailure(key, FailureCategory.CONNECTION_REFUSED, permissive, 1);
            } catch (InterruptedException ex) {
              Thread.currentThread().interrupt();
            } finally {
              done.countDown();
            }
          });
    }

    start.countDown();
    assertTrue(done.await(5, TimeUnit.SECONDS));
    executor.shutdownNow();

    assertTrue(registry.getState(key).consecutiveQualifyingFailures() > 0);
  }

  @Test
  void reconcilePreservesUnchangedFingerprintsAndResetsChangedOnes() {
    TargetKey key = new TargetKey(API_ID, POOL_ID, TARGET_A);
    URI unchangedUrl = URI.create("http://127.0.0.1:8080");
    registry.reconcile(
        API_ID,
        Map.of(
            POOL_ID,
            List.of(
                TargetFingerprint.of(TARGET_A, unchangedUrl),
                TargetFingerprint.of(TARGET_B, URI.create("http://127.0.0.1:8081")))));

    registry.recordFailure(key, FailureCategory.CONNECTION_REFUSED, policy, 2);
    registry.recordFailure(key, FailureCategory.CONNECTION_REFUSED, policy, 2);
    assertTrue(registry.isEjected(key));

    registry.reconcile(
        API_ID,
        Map.of(
            POOL_ID,
            List.of(
                TargetFingerprint.of(TARGET_A, unchangedUrl),
                TargetFingerprint.of(TARGET_B, URI.create("http://127.0.0.1:8081")))));
    assertTrue(registry.isEjected(key));

    registry.reconcile(
        API_ID,
        Map.of(
            POOL_ID,
            List.of(
                TargetFingerprint.of(TARGET_A, URI.create("http://127.0.0.1:9090")),
                TargetFingerprint.of(TARGET_B, URI.create("http://127.0.0.1:8081")))));
    assertFalse(registry.isEjected(key));
    assertEquals(0, registry.getState(key).consecutiveQualifyingFailures());
  }

  @Test
  void reconcileRemovesStaleTargets() {
    TargetKey stale = new TargetKey(API_ID, POOL_ID, TARGET_C);
    registry.recordFailure(stale, FailureCategory.CONNECTION_REFUSED, policy, 2);

    registry.reconcile(
        API_ID,
        Map.of(
            POOL_ID, List.of(TargetFingerprint.of(TARGET_A, URI.create("http://127.0.0.1:8080")))));

    assertEquals(
        TargetHealthState.healthy(clock.instant()).consecutiveQualifyingFailures(),
        registry.getState(stale).consecutiveQualifyingFailures());
  }
}
