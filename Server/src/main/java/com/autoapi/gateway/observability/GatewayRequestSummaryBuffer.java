package com.autoapi.gateway.observability;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Bounded in-memory request summary ring buffer for gateway-local inspection. */
public class GatewayRequestSummaryBuffer {

  private final int capacity;
  private final Deque<GatewayRequestSummary> summaries = new ArrayDeque<>();

  public GatewayRequestSummaryBuffer(GatewayObservabilityProperties properties) {
    this.capacity = Math.max(10, properties.requestSummaryBufferSize());
  }

  public synchronized void offer(GatewayRequestSummary summary) {
    if (summary == null) {
      return;
    }
    summaries.addFirst(summary);
    while (summaries.size() > capacity) {
      summaries.removeLast();
    }
  }

  public synchronized List<GatewayRequestSummary> recent(int limit) {
    int effectiveLimit = Math.max(1, Math.min(limit, capacity));
    List<GatewayRequestSummary> result = new ArrayList<>(effectiveLimit);
    int count = 0;
    for (GatewayRequestSummary summary : summaries) {
      result.add(summary);
      count++;
      if (count >= effectiveLimit) {
        break;
      }
    }
    return result;
  }

  public synchronized List<GatewayRequestSummary> drainForExport(int maxBatchSize) {
    int batchSize = Math.max(1, maxBatchSize);
    List<GatewayRequestSummary> batch = new ArrayList<>(batchSize);
    while (!summaries.isEmpty() && batch.size() < batchSize) {
      batch.add(summaries.removeLast());
    }
    return batch;
  }

  public synchronized int size() {
    return summaries.size();
  }
}
