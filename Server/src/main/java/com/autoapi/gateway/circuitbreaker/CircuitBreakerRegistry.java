package com.autoapi.gateway.circuitbreaker;

import com.autoapi.config.RuntimeCircuitBreakerPolicyConfig;
import com.autoapi.gateway.health.TargetKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Gateway-local per-target circuit breaker registry. State is never synchronized across gateways.
 */
public final class CircuitBreakerRegistry {

  private static final Logger log = LoggerFactory.getLogger(CircuitBreakerRegistry.class);

  private final Clock clock;
  private final String gatewayId;
  private final ObjectProvider<GatewayCircuitBreakerMetrics> metricsProvider;
  private final ConcurrentHashMap<TargetKey, CircuitBreakerTargetState> states =
      new ConcurrentHashMap<>();

  public CircuitBreakerRegistry(
      Clock clock, String gatewayId, ObjectProvider<GatewayCircuitBreakerMetrics> metricsProvider) {
    this.clock = clock;
    this.gatewayId = gatewayId;
    this.metricsProvider = metricsProvider;
  }

  public CircuitAdmission tryAdmit(
      TargetKey targetKey, RuntimeCircuitBreakerPolicyConfig policy, UUID apiId, String routeId) {
    if (policy == null || !policy.circuitBreakerEnabled()) {
      return CircuitAdmission.ALLOW;
    }
    CircuitBreakerTargetState state =
        states.computeIfAbsent(targetKey, ignored -> new CircuitBreakerTargetState());
    Instant now = clock.instant();
    synchronized (state) {
      advanceStateIfNeeded(state, policy, now, targetKey, apiId, routeId);
      return switch (state.state()) {
        case CLOSED -> CircuitAdmission.ALLOW;
        case OPEN -> {
          recordRejected(targetKey, policy, apiId, routeId, "OPEN");
          yield CircuitAdmission.REJECT_OPEN;
        }
        case HALF_OPEN -> {
          if (state.halfOpenActiveProbes() >= policy.halfOpenMaxRequests()) {
            recordRejected(targetKey, policy, apiId, routeId, "HALF_OPEN_FULL");
            yield CircuitAdmission.REJECT_HALF_OPEN_FULL;
          }
          state.incrementHalfOpenProbe();
          yield CircuitAdmission.ALLOW;
        }
      };
    }
  }

  public void recordSuccess(
      TargetKey targetKey, RuntimeCircuitBreakerPolicyConfig policy, UUID apiId, String routeId) {
    if (policy == null || !policy.circuitBreakerEnabled()) {
      return;
    }
    CircuitBreakerTargetState state = states.get(targetKey);
    if (state == null) {
      return;
    }
    Instant now = clock.instant();
    synchronized (state) {
      advanceStateIfNeeded(state, policy, now, targetKey, apiId, routeId);
      if (state.state() == CircuitBreakerState.HALF_OPEN) {
        state.releaseHalfOpenProbe();
        state.recordHalfOpenSuccess();
        metricsProvider
            .getIfAvailable()
            .recordHalfOpenSuccess(routeId, policy.policyId().toString());
        if (state.halfOpenSuccesses() >= policy.successThreshold()) {
          state.transitionToClosed();
          recordStateMetric(routeId, policy, CircuitBreakerState.CLOSED);
          metricsProvider.getIfAvailable().recordRecovery(routeId, policy.policyId().toString());
          log.info(
              "Circuit breaker closed gatewayId={} apiId={} routeId={} targetId={} policyId={} reason=recovery_succeeded",
              gatewayId,
              apiId,
              routeId,
              targetKey.targetId(),
              policy.policyId());
        }
      }
    }
  }

  public void recordFailure(
      TargetKey targetKey,
      RuntimeCircuitBreakerPolicyConfig policy,
      UUID apiId,
      String routeId,
      String reason) {
    if (policy == null || !policy.circuitBreakerEnabled()) {
      return;
    }
    CircuitBreakerTargetState state =
        states.computeIfAbsent(targetKey, ignored -> new CircuitBreakerTargetState());
    Instant now = clock.instant();
    synchronized (state) {
      advanceStateIfNeeded(state, policy, now, targetKey, apiId, routeId);
      if (state.state() == CircuitBreakerState.HALF_OPEN) {
        state.releaseHalfOpenProbe();
        state.transitionToOpen(now);
        recordStateMetric(routeId, policy, CircuitBreakerState.OPEN);
        metricsProvider.getIfAvailable().recordTrip(routeId, policy.policyId().toString());
        log.warn(
            "Circuit breaker opened gatewayId={} apiId={} routeId={} targetId={} policyId={} reason={}",
            gatewayId,
            apiId,
            routeId,
            targetKey.targetId(),
            policy.policyId(),
            reason);
        return;
      }
      Duration window = Duration.ofSeconds(policy.rollingWindowSeconds());
      state.pruneFailures(now.minus(window));
      state.recordFailure(now);
      if (state.failureCount() >= policy.failureThreshold()) {
        state.transitionToOpen(now);
        recordStateMetric(routeId, policy, CircuitBreakerState.OPEN);
        metricsProvider.getIfAvailable().recordTrip(routeId, policy.policyId().toString());
        log.warn(
            "Circuit breaker opened gatewayId={} apiId={} routeId={} targetId={} policyId={} reason={}",
            gatewayId,
            apiId,
            routeId,
            targetKey.targetId(),
            policy.policyId(),
            reason);
      }
    }
  }

  public CircuitBreakerState getState(TargetKey targetKey) {
    CircuitBreakerTargetState state = states.get(targetKey);
    return state == null ? CircuitBreakerState.CLOSED : state.state();
  }

  public Map<TargetKey, CircuitBreakerState> snapshotStates() {
    Map<TargetKey, CircuitBreakerState> snapshot = new ConcurrentHashMap<>();
    states.forEach((key, state) -> snapshot.put(key, state.state()));
    return Map.copyOf(snapshot);
  }

  private void advanceStateIfNeeded(
      CircuitBreakerTargetState state,
      RuntimeCircuitBreakerPolicyConfig policy,
      Instant now,
      TargetKey targetKey,
      UUID apiId,
      String routeId) {
    if (state.state() == CircuitBreakerState.OPEN
        && state.openedAt() != null
        && !now.isBefore(state.openedAt().plus(Duration.ofSeconds(policy.openDurationSeconds())))) {
      state.transitionToHalfOpen();
      recordStateMetric(routeId, policy, CircuitBreakerState.HALF_OPEN);
      log.info(
          "Circuit breaker half-open gatewayId={} apiId={} routeId={} targetId={} policyId={} reason=cooldown_expired",
          gatewayId,
          apiId,
          routeId,
          targetKey.targetId(),
          policy.policyId());
    }
  }

  private void recordRejected(
      TargetKey targetKey,
      RuntimeCircuitBreakerPolicyConfig policy,
      UUID apiId,
      String routeId,
      String reason) {
    GatewayCircuitBreakerMetrics metrics = metricsProvider.getIfAvailable();
    if (metrics != null) {
      metrics.recordRejectedRequest(routeId, policy.policyId().toString());
    }
    log.debug(
        "Circuit breaker rejected request gatewayId={} apiId={} routeId={} targetId={} policyId={} reason={}",
        gatewayId,
        apiId,
        routeId,
        targetKey.targetId(),
        policy.policyId(),
        reason);
  }

  private void recordStateMetric(
      String routeId, RuntimeCircuitBreakerPolicyConfig policy, CircuitBreakerState state) {
    GatewayCircuitBreakerMetrics metrics = metricsProvider.getIfAvailable();
    if (metrics == null) {
      return;
    }
    String policyId = policy.policyId().toString();
    switch (state) {
      case OPEN -> metrics.recordOpen(routeId, policyId);
      case HALF_OPEN -> metrics.recordHalfOpen(routeId, policyId);
      case CLOSED -> metrics.recordClosed(routeId, policyId);
    }
  }
}
