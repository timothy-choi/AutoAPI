package com.autoapi.gateway.traffic;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

@Component
public class TrafficSplitRegistry {

  private final ConcurrentHashMap<CounterKey, CounterState> counters = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<UUID, String> lastFingerprintByPolicy = new ConcurrentHashMap<>();

  public void recordAssignment(
      String routeId, UUID policyId, UUID destinationId, String destinationName, boolean fallback) {
    CounterState state =
        counters.computeIfAbsent(
            new CounterKey(routeId, policyId, destinationId),
            k -> new CounterState(destinationName));
    state.assignedRequests.incrementAndGet();
    if (fallback) {
      state.fallbackRequests.incrementAndGet();
    }
  }

  public void recordUnavailable(String routeId, UUID policyId) {
    counters
        .computeIfAbsent(
            new CounterKey(routeId, policyId, null), k -> new CounterState("unavailable"))
        .unavailableRequests
        .incrementAndGet();
  }

  public void reconcile(Set<UUID> activePolicyIds, Map<UUID, String> fingerprintByPolicy) {
    counters
        .keySet()
        .removeIf(key -> key.policyId() != null && !activePolicyIds.contains(key.policyId()));
    lastFingerprintByPolicy.keySet().removeIf(id -> !activePolicyIds.contains(id));
    for (Map.Entry<UUID, String> entry : fingerprintByPolicy.entrySet()) {
      UUID policyId = entry.getKey();
      String fingerprint = entry.getValue();
      String previous = lastFingerprintByPolicy.get(policyId);
      if (previous != null && !previous.equals(fingerprint)) {
        counters.keySet().removeIf(key -> policyId.equals(key.policyId()));
      }
      lastFingerprintByPolicy.put(policyId, fingerprint);
    }
  }

  public Map<CounterKey, CounterState> snapshot() {
    return Map.copyOf(counters);
  }

  public record CounterKey(String routeId, UUID policyId, UUID destinationId) {}

  public static final class CounterState {
    private final String destinationName;
    private final AtomicLong assignedRequests = new AtomicLong();
    private final AtomicLong fallbackRequests = new AtomicLong();
    private final AtomicLong unavailableRequests = new AtomicLong();

    CounterState(String destinationName) {
      this.destinationName = destinationName;
    }

    public String destinationName() {
      return destinationName;
    }

    public long assignedRequests() {
      return assignedRequests.get();
    }

    public long fallbackRequests() {
      return fallbackRequests.get();
    }

    public long unavailableRequests() {
      return unavailableRequests.get();
    }
  }
}
