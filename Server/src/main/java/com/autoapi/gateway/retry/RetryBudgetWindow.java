package com.autoapi.gateway.retry;

import com.autoapi.config.RuntimeRetryPolicyConfig;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.AtomicReference;

/** Gateway-local sliding-window retry budget for one route policy. */
final class RetryBudgetWindow {

  private final int windowSeconds;
  private final int budgetPercent;
  private final int budgetMinRetriesPerSecond;
  private final AtomicLongArray originalRequests;
  private final AtomicLongArray retriesUsed;
  private final AtomicReference<Long> lastSecond = new AtomicReference<>(0L);

  RetryBudgetWindow(RuntimeRetryPolicyConfig policy) {
    this.windowSeconds = policy.budgetWindowSeconds();
    this.budgetPercent = policy.budgetPercent();
    this.budgetMinRetriesPerSecond = policy.budgetMinRetriesPerSecond();
    this.originalRequests = new AtomicLongArray(windowSeconds);
    this.retriesUsed = new AtomicLongArray(windowSeconds);
  }

  void recordOriginalRequest(Clock clock) {
    increment(originalRequests, clock);
  }

  boolean tryConsumeRetry(Clock clock) {
    roll(clock);
    long capacity = retryCapacity();
    long used = sum(retriesUsed);
    if (used >= capacity) {
      return false;
    }
    increment(retriesUsed, clock);
    return true;
  }

  RetryBudgetSnapshot snapshot(Clock clock) {
    roll(clock);
    return new RetryBudgetSnapshot(
        windowSeconds, sum(originalRequests), sum(retriesUsed), retryCapacity());
  }

  long retryCapacity() {
    long originals = sum(originalRequests);
    long percentCapacity = (originals * budgetPercent) / 100L;
    long floor = (long) budgetMinRetriesPerSecond * windowSeconds;
    return Math.max(floor, percentCapacity);
  }

  private void increment(AtomicLongArray buckets, Clock clock) {
    roll(clock);
    int index = bucketIndex(clock.instant());
    buckets.incrementAndGet(index);
  }

  private void roll(Clock clock) {
    long nowSecond = clock.instant().getEpochSecond();
    Long previous = lastSecond.get();
    if (previous == null || previous == 0L) {
      lastSecond.set(nowSecond);
      return;
    }
    if (nowSecond <= previous) {
      return;
    }
    if (lastSecond.compareAndSet(previous, nowSecond)) {
      long elapsed = Math.min(nowSecond - previous, windowSeconds);
      for (long second = previous + 1; second <= previous + elapsed; second++) {
        int index = (int) Math.floorMod(second, windowSeconds);
        originalRequests.set(index, 0);
        retriesUsed.set(index, 0);
      }
    }
  }

  private static long sum(AtomicLongArray buckets) {
    long total = 0;
    for (int i = 0; i < buckets.length(); i++) {
      total += buckets.get(i);
    }
    return total;
  }

  private int bucketIndex(Instant instant) {
    return Math.floorMod(instant.getEpochSecond(), windowSeconds);
  }

  record RetryBudgetSnapshot(
      int windowSeconds, long originalRequests, long retriesUsed, long retryCapacity) {}
}
