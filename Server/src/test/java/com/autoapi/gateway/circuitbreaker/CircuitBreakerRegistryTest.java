package com.autoapi.gateway.circuitbreaker;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoapi.config.RuntimeCircuitBreakerFailurePredicate;
import com.autoapi.config.RuntimeCircuitBreakerPolicyConfig;
import com.autoapi.gateway.health.TargetKey;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class CircuitBreakerRegistryTest {

  private static final UUID API_ID = UUID.randomUUID();
  private static final UUID POOL_ID = UUID.randomUUID();
  private static final UUID TARGET_ID = UUID.randomUUID();
  private static final String ROUTE_ID = "route-1";

  private MutableClock clock;
  private CircuitBreakerRegistry registry;
  private RuntimeCircuitBreakerPolicyConfig policy;

  @BeforeEach
  void setUp() {
    clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    ObjectProvider<GatewayCircuitBreakerMetrics> metricsProvider =
        new ObjectProvider<>() {
          @Override
          public GatewayCircuitBreakerMetrics getObject() {
            return new GatewayCircuitBreakerMetrics(new SimpleMeterRegistry());
          }

          @Override
          public GatewayCircuitBreakerMetrics getObject(Object... args) {
            return getObject();
          }

          @Override
          public GatewayCircuitBreakerMetrics getIfAvailable() {
            return getObject();
          }

          @Override
          public GatewayCircuitBreakerMetrics getIfUnique() {
            return getObject();
          }
        };
    registry = new CircuitBreakerRegistry(clock, "gw-test", metricsProvider);
    policy =
        new RuntimeCircuitBreakerPolicyConfig(
            UUID.randomUUID(),
            2,
            30,
            5,
            1,
            1,
            new RuntimeCircuitBreakerFailurePredicate(true, true, true, true, true, true, false));
  }

  @Test
  void opensAfterRollingFailureThreshold() {
    TargetKey key = new TargetKey(API_ID, POOL_ID, TARGET_ID);
    assertThat(registry.tryAdmit(key, policy, API_ID, ROUTE_ID)).isEqualTo(CircuitAdmission.ALLOW);
    registry.recordFailure(key, policy, API_ID, ROUTE_ID, "http_503");
    assertThat(registry.getState(key)).isEqualTo(CircuitBreakerState.CLOSED);
    registry.recordFailure(key, policy, API_ID, ROUTE_ID, "http_503");
    assertThat(registry.getState(key)).isEqualTo(CircuitBreakerState.OPEN);
    assertThat(registry.tryAdmit(key, policy, API_ID, ROUTE_ID))
        .isEqualTo(CircuitAdmission.REJECT_OPEN);
  }

  @Test
  void halfOpenProbeRecoversToClosed() {
    TargetKey key = new TargetKey(API_ID, POOL_ID, TARGET_ID);
    registry.recordFailure(key, policy, API_ID, ROUTE_ID, "http_503");
    registry.recordFailure(key, policy, API_ID, ROUTE_ID, "http_503");
    assertThat(registry.getState(key)).isEqualTo(CircuitBreakerState.OPEN);

    clock.advanceSeconds(5);
    assertThat(registry.tryAdmit(key, policy, API_ID, ROUTE_ID)).isEqualTo(CircuitAdmission.ALLOW);
    assertThat(registry.getState(key)).isEqualTo(CircuitBreakerState.HALF_OPEN);

    registry.recordSuccess(key, policy, API_ID, ROUTE_ID);
    assertThat(registry.getState(key)).isEqualTo(CircuitBreakerState.CLOSED);
    assertThat(registry.tryAdmit(key, policy, API_ID, ROUTE_ID)).isEqualTo(CircuitAdmission.ALLOW);
  }

  @Test
  void halfOpenFailureReturnsToOpen() {
    TargetKey key = new TargetKey(API_ID, POOL_ID, TARGET_ID);
    registry.recordFailure(key, policy, API_ID, ROUTE_ID, "http_503");
    registry.recordFailure(key, policy, API_ID, ROUTE_ID, "http_503");
    clock.advanceSeconds(5);
    assertThat(registry.tryAdmit(key, policy, API_ID, ROUTE_ID)).isEqualTo(CircuitAdmission.ALLOW);
    registry.recordFailure(key, policy, API_ID, ROUTE_ID, "http_503");
    assertThat(registry.getState(key)).isEqualTo(CircuitBreakerState.OPEN);
  }

  private static final class MutableClock extends Clock {
    private Instant instant;

    private MutableClock(Instant instant) {
      this.instant = instant;
    }

    void advanceSeconds(long seconds) {
      instant = instant.plusSeconds(seconds);
    }

    @Override
    public ZoneOffset getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return instant;
    }
  }
}
