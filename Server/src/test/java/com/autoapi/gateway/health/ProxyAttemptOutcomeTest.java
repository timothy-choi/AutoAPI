package com.autoapi.gateway.health;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ProxyAttemptOutcomeTest {

  @Test
  void recordsSuccessExactlyOnce() {
    ProxyAttemptOutcome outcome = new ProxyAttemptOutcome();
    AtomicInteger successRuns = new AtomicInteger();
    AtomicInteger failureRuns = new AtomicInteger();

    assertTrue(outcome.recordSuccess(successRuns::incrementAndGet));
    assertFalse(outcome.recordSuccess(successRuns::incrementAndGet));
    assertFalse(outcome.recordFailure(failureRuns::incrementAndGet));

    assertTrue(outcome.isRecorded());
    assertEquals(1, successRuns.get());
    assertEquals(0, failureRuns.get());
  }

  @Test
  void recordsFailureExactlyOnce() {
    ProxyAttemptOutcome outcome = new ProxyAttemptOutcome();
    AtomicInteger successRuns = new AtomicInteger();
    AtomicInteger failureRuns = new AtomicInteger();

    assertTrue(outcome.recordFailure(failureRuns::incrementAndGet));
    assertFalse(outcome.recordFailure(failureRuns::incrementAndGet));
    assertFalse(outcome.recordSuccess(successRuns::incrementAndGet));

    assertTrue(outcome.isRecorded());
    assertEquals(0, successRuns.get());
    assertEquals(1, failureRuns.get());
  }
}
