package com.autoapi.controlplane.rollout;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = {"autoapi.controlplane.enabled", "autoapi.rollouts.enabled"},
    havingValue = "true",
    matchIfMissing = true)
public class RolloutsMetrics {

  private final MeterRegistry meterRegistry;
  private final AtomicLong activeRollouts = new AtomicLong(0);
  private final AtomicLong gatewaysAssigned = new AtomicLong(0);
  private final AtomicLong gatewaysConverged = new AtomicLong(0);
  private final AtomicLong gatewayGroups = new AtomicLong(0);
  private final AtomicLong gatewayGroupMembers = new AtomicLong(0);

  public RolloutsMetrics(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
    Gauge.builder("autoapi_runtime_rollouts_active", activeRollouts, AtomicLong::get)
        .description("Active runtime rollouts")
        .register(meterRegistry);
    Gauge.builder("autoapi_runtime_rollout_gateways_assigned", gatewaysAssigned, AtomicLong::get)
        .description("Gateways assigned in reconciled rollout stage")
        .register(meterRegistry);
    Gauge.builder("autoapi_runtime_rollout_gateways_converged", gatewaysConverged, AtomicLong::get)
        .description("Gateways converged in reconciled rollout stage")
        .register(meterRegistry);
    Gauge.builder("autoapi_gateway_groups", gatewayGroups, AtomicLong::get)
        .description("Gateway groups")
        .register(meterRegistry);
    Gauge.builder("autoapi_gateway_group_members", gatewayGroupMembers, AtomicLong::get)
        .description("Gateway group members")
        .register(meterRegistry);
  }

  public void recordRolloutCreated(String strategy, String progressionMode) {
    counter(
            "autoapi_runtime_rollouts_total",
            "strategy",
            strategy,
            "progression_mode",
            progressionMode)
        .increment();
  }

  public void recordStageTransition(String strategy, String fromStatus, String toStatus) {
    counter(
            "autoapi_runtime_rollout_stage_transitions_total",
            "strategy",
            strategy,
            "result",
            fromStatus + "_TO_" + toStatus)
        .increment();
  }

  public void recordGatewayFailure(String strategy, String failureCode) {
    counter(
            "autoapi_runtime_rollout_gateway_failures_total",
            "strategy",
            strategy,
            "failure_code",
            failureCode)
        .increment();
  }

  public void recordGatewayTimeout(String strategy) {
    counter("autoapi_runtime_rollout_gateway_timeouts_total", "strategy", strategy).increment();
  }

  public void recordPause(String strategy) {
    counter("autoapi_runtime_rollout_pauses_total", "strategy", strategy).increment();
  }

  public void recordRollback(String strategy, boolean automatic) {
    counter(
            "autoapi_runtime_rollout_rollbacks_total",
            "strategy",
            strategy,
            "automatic",
            automatic ? "true" : "false")
        .increment();
  }

  public void recordRollbackFailure(String strategy) {
    counter("autoapi_runtime_rollout_rollback_failures_total", "strategy", strategy).increment();
  }

  public void recordRolloutFailure(String strategy, String failureCode) {
    counter(
            "autoapi_runtime_rollouts_total",
            "strategy",
            strategy,
            "status",
            "failed",
            "failure_code",
            failureCode)
        .increment();
  }

  public void recordReconcilerRun() {
    Counter.builder("autoapi_runtime_rollout_reconciler_runs_total")
        .register(meterRegistry)
        .increment();
  }

  public void recordReconcilerFailure() {
    Counter.builder("autoapi_runtime_rollout_reconciler_failures_total")
        .register(meterRegistry)
        .increment();
  }

  public void setActiveRollouts(long count) {
    activeRollouts.set(count);
  }

  public void setGatewaysAssigned(String strategy, long count) {
    gatewaysAssigned.set(count);
  }

  public void setGatewaysConverged(String strategy, long count) {
    gatewaysConverged.set(count);
  }

  public void setGatewayGroups(long count) {
    gatewayGroups.set(count);
  }

  public void setGatewayGroupMembers(long count) {
    gatewayGroupMembers.set(count);
  }

  public Timer.Sample startRolloutDurationSample() {
    return Timer.start(meterRegistry);
  }

  public void recordRolloutDuration(Timer.Sample sample, String strategy, String status) {
    sample.stop(
        Timer.builder("autoapi_runtime_rollout_duration_seconds")
            .tag("strategy", bounded(strategy))
            .tag("status", bounded(status))
            .register(meterRegistry));
  }

  public void recordStageDuration(String strategy, String result, long durationMs) {
    Timer.builder("autoapi_runtime_rollout_stage_duration_seconds")
        .tag("strategy", bounded(strategy))
        .tag("result", bounded(result))
        .register(meterRegistry)
        .record(java.time.Duration.ofMillis(durationMs));
  }

  private Counter counter(String name, String... tags) {
    Counter.Builder builder = Counter.builder(name);
    for (int i = 0; i + 1 < tags.length; i += 2) {
      builder.tag(tags[i], bounded(tags[i + 1]));
    }
    return builder.register(meterRegistry);
  }

  private static String bounded(String value) {
    if (value == null || value.isBlank()) {
      return "unknown";
    }
    String normalized = value.trim().toLowerCase();
    if (normalized.length() > 32) {
      return normalized.substring(0, 32);
    }
    return normalized;
  }
}
