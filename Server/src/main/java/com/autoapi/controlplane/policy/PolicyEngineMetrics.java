package com.autoapi.controlplane.policy;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = "autoapi.controlplane.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class PolicyEngineMetrics {

  private final Counter evaluateCounter;
  private final Counter cacheHitCounter;
  private final Counter cacheMissCounter;
  private final AtomicLong bundleAssignments = new AtomicLong(0);
  private final AtomicLong overrideCount = new AtomicLong(0);
  private final AtomicLong inheritanceDepth = new AtomicLong(0);

  public PolicyEngineMetrics(MeterRegistry meterRegistry) {
    this.evaluateCounter =
        Counter.builder("policy.evaluate")
            .description("Effective policy evaluations")
            .register(meterRegistry);
    this.cacheHitCounter =
        Counter.builder("policy.cache.hit")
            .description("Effective policy cache hits")
            .register(meterRegistry);
    this.cacheMissCounter =
        Counter.builder("policy.cache.miss")
            .description("Effective policy cache misses")
            .register(meterRegistry);
    Gauge.builder("policy.bundle.assignments", bundleAssignments, AtomicLong::get)
        .description("Policy bundle assignments observed during evaluation")
        .register(meterRegistry);
    Gauge.builder("policy.override.count", overrideCount, AtomicLong::get)
        .description("Policy overrides observed during evaluation")
        .register(meterRegistry);
    Gauge.builder("policy.inheritance.depth", inheritanceDepth, AtomicLong::get)
        .description("Policy inheritance depth for last evaluation")
        .register(meterRegistry);
  }

  public void recordEvaluate() {
    evaluateCounter.increment();
  }

  public void recordCacheHit() {
    cacheHitCounter.increment();
  }

  public void recordCacheMiss() {
    cacheMissCounter.increment();
  }

  public void recordBundleAssignment() {
    bundleAssignments.incrementAndGet();
  }

  public void recordOverride() {
    overrideCount.incrementAndGet();
  }

  public void recordInheritanceDepth(int depth) {
    inheritanceDepth.set(depth);
  }

  public void resetEvaluationCounters() {
    bundleAssignments.set(0);
    overrideCount.set(0);
  }
}
