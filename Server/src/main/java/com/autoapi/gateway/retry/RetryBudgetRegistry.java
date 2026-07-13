package com.autoapi.gateway.retry;

import com.autoapi.config.RuntimeRetryPolicyConfig;
import com.autoapi.gateway.config.ActiveRuntimeBundle;
import java.time.Clock;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Gateway-local retry budget registry keyed by api/route/policy identity. */
public final class RetryBudgetRegistry {

  private final Clock clock;
  private final ConcurrentHashMap<RetryBudgetKey, Entry> budgets = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<RetryBudgetKey, RetryPolicyFingerprint> fingerprints =
      new ConcurrentHashMap<>();

  public RetryBudgetRegistry(Clock clock) {
    this.clock = clock;
  }

  public void recordOriginalRequest(RetryBudgetKey key, RuntimeRetryPolicyConfig policy) {
    if (key == null || policy == null) {
      return;
    }
    entryFor(key, policy).window.recordOriginalRequest(clock);
  }

  public boolean tryConsumeRetry(RetryBudgetKey key, RuntimeRetryPolicyConfig policy) {
    if (key == null || policy == null) {
      return false;
    }
    return entryFor(key, policy).window.tryConsumeRetry(clock);
  }

  public RetryBudgetWindow.RetryBudgetSnapshot snapshot(
      RetryBudgetKey key, RuntimeRetryPolicyConfig policy) {
    if (key == null || policy == null) {
      return new RetryBudgetWindow.RetryBudgetSnapshot(0, 0, 0, 0);
    }
    return entryFor(key, policy).window.snapshot(clock);
  }

  public List<BudgetView> activeBudgetViews() {
    return budgets.entrySet().stream()
        .map(
            entry -> {
              RetryBudgetKey key = entry.getKey();
              Entry value = entry.getValue();
              RetryBudgetWindow.RetryBudgetSnapshot snapshot = value.window.snapshot(clock);
              return new BudgetView(
                  key.apiId(),
                  key.routeId(),
                  key.policyId(),
                  snapshot.windowSeconds(),
                  snapshot.originalRequests(),
                  snapshot.retriesUsed(),
                  snapshot.retryCapacity());
            })
        .toList();
  }

  public void reconcile(ActiveRuntimeBundle bundle) {
    Map<RetryBudgetKey, RetryPolicyFingerprint> active = new HashMap<>();
    bundle
        .runtimeConfig()
        .routes()
        .forEach(
            route -> {
              RuntimeRetryPolicyConfig retry = route.retry();
              if (retry == null || !retry.retriesEnabled()) {
                return;
              }
              RetryBudgetKey key = new RetryBudgetKey(bundle.apiId(), route.id(), retry.policyId());
              active.put(key, RetryPolicyFingerprint.from(retry));
            });

    Set<RetryBudgetKey> activeKeys = new HashSet<>(active.keySet());
    for (Map.Entry<RetryBudgetKey, RetryPolicyFingerprint> entry : active.entrySet()) {
      RetryPolicyFingerprint previous = fingerprints.get(entry.getKey());
      if (previous == null || !previous.equals(entry.getValue())) {
        budgets.remove(entry.getKey());
      }
      fingerprints.put(entry.getKey(), entry.getValue());
    }

    budgets
        .keySet()
        .removeIf(key -> key.apiId().equals(bundle.apiId()) && !activeKeys.contains(key));
    fingerprints
        .keySet()
        .removeIf(key -> key.apiId().equals(bundle.apiId()) && !activeKeys.contains(key));
  }

  private Entry entryFor(RetryBudgetKey key, RuntimeRetryPolicyConfig policy) {
    return budgets.computeIfAbsent(key, ignored -> new Entry(new RetryBudgetWindow(policy)));
  }

  private static final class Entry {
    private final RetryBudgetWindow window;

    private Entry(RetryBudgetWindow window) {
      this.window = window;
    }
  }

  public record BudgetView(
      java.util.UUID apiId,
      String routeId,
      java.util.UUID policyId,
      int windowSeconds,
      long originalRequests,
      long retriesUsed,
      long retryCapacity) {}
}
