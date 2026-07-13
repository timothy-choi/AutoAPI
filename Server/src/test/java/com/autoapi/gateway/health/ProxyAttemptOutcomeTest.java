package com.autoapi.gateway.health;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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

  @Test
  void concurrentCallbacksFinalizeOnce() throws Exception {
    ProxyAttemptOutcome outcome = new ProxyAttemptOutcome();
    AtomicInteger successRuns = new AtomicInteger();
    AtomicInteger failureRuns = new AtomicInteger();
    int workers = 16;
    ExecutorService executor = Executors.newFixedThreadPool(workers);
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(workers);

    for (int i = 0; i < workers; i++) {
      final int index = i;
      executor.submit(
          () -> {
            try {
              start.await();
              if (index % 2 == 0) {
                outcome.recordSuccess(successRuns::incrementAndGet);
              } else {
                outcome.recordFailure(failureRuns::incrementAndGet);
              }
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

    assertTrue(outcome.isRecorded());
    assertEquals(1, successRuns.get() + failureRuns.get());
  }

  @Test
  void coordinatorIsNewPerAttempt() {
    ProxyAttemptOutcome first = new ProxyAttemptOutcome();
    ProxyAttemptOutcome second = new ProxyAttemptOutcome();
    AtomicInteger firstRuns = new AtomicInteger();
    AtomicInteger secondRuns = new AtomicInteger();

    assertTrue(first.recordFailure(firstRuns::incrementAndGet));
    assertTrue(second.recordSuccess(secondRuns::incrementAndGet));

    assertEquals(1, firstRuns.get());
    assertEquals(1, secondRuns.get());
    assertTrue(first.isRecorded());
    assertTrue(second.isRecorded());
  }
}
