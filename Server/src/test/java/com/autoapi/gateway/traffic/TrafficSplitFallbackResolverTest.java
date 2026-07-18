package com.autoapi.gateway.traffic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.autoapi.config.GatewayConfig;
import com.autoapi.config.RuntimeConfig;
import com.autoapi.config.RuntimeTrafficSplitConfig;
import com.autoapi.config.RuntimeTrafficSplitDestination;
import com.autoapi.config.UpstreamConfig;
import com.autoapi.config.UpstreamTargetReference;
import com.autoapi.gateway.circuitbreaker.CircuitBreakerRegistry;
import com.autoapi.gateway.circuitbreaker.GatewayTargetSelector;
import com.autoapi.gateway.config.ActiveRuntimeBundle;
import com.autoapi.gateway.health.FailureCategory;
import com.autoapi.gateway.health.HealthAwareTargetSelector;
import com.autoapi.gateway.health.PassiveHealthPolicy;
import com.autoapi.gateway.health.TargetHealthRegistry;
import com.autoapi.gateway.health.TargetKey;
import com.autoapi.support.ControllableClock;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class TrafficSplitFallbackResolverTest {

  private static final UUID API_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID STABLE_POOL = UUID.fromString("00000000-0000-0000-0000-000000000010");
  private static final UUID CANARY_POOL = UUID.fromString("00000000-0000-0000-0000-000000000011");
  private static final UUID STABLE_TARGET = UUID.fromString("00000000-0000-0000-0000-000000000020");
  private static final UUID CANARY_TARGET = UUID.fromString("00000000-0000-0000-0000-000000000021");
  private static final UUID STABLE_DEST = UUID.fromString("00000000-0000-0000-0000-000000000030");
  private static final UUID CANARY_DEST = UUID.fromString("00000000-0000-0000-0000-000000000031");
  private static final UUID POLICY_ID = UUID.fromString("00000000-0000-0000-0000-000000000040");

  private static final PassiveHealthPolicy HEALTH_POLICY =
      new PassiveHealthPolicy(2, Duration.ofSeconds(30), 100);

  private final ControllableClock clock =
      ControllableClock.fixed(Instant.parse("2026-01-01T00:00:00Z"));
  private TargetHealthRegistry registry;
  private GatewayTargetSelector selector;
  private ActiveRuntimeBundle bundle;

  @BeforeEach
  void setUp() {
    registry = new TargetHealthRegistry(clock);
    @SuppressWarnings("unchecked")
    ObjectProvider<com.autoapi.gateway.circuitbreaker.GatewayCircuitBreakerMetrics>
        metricsProvider = org.mockito.Mockito.mock(ObjectProvider.class);
    org.mockito.Mockito.when(metricsProvider.getIfAvailable()).thenReturn(null);
    CircuitBreakerRegistry circuitRegistry =
        new CircuitBreakerRegistry(clock, "test-gateway", metricsProvider);
    selector =
        new GatewayTargetSelector(new HealthAwareTargetSelector(registry, clock), circuitRegistry);
    bundle =
        new ActiveRuntimeBundle(
            API_ID, 1, "hash", new RuntimeConfig(new GatewayConfig("0.0.0.0", 8080), List.of()));
  }

  @Test
  void keepsNominalCanaryBeforePassiveEjection() {
    RuntimeTrafficSplitConfig config = fallbackConfig("FALLBACK_TO_PRIMARY");
    RuntimeTrafficSplitDestination nominal = canaryDestination();

    Optional<RuntimeTrafficSplitDestination> effective =
        TrafficSplitFallbackResolver.resolveEffectiveDestination(
            config, nominal, bundle, selector, null, "test-route");

    assertTrue(effective.isPresent());
    assertEquals(CANARY_DEST, effective.get().destinationId());
    assertEquals(
        TrafficSplitFallbackResolver.FallbackReason.NONE,
        TrafficSplitFallbackResolver.fallbackReason(config, nominal, effective.get()));
  }

  @Test
  void fallsBackToPrimaryAfterCanaryTargetEjected() {
    ejectCanaryTarget();

    RuntimeTrafficSplitConfig config = fallbackConfig("FALLBACK_TO_PRIMARY");
    RuntimeTrafficSplitDestination nominal = canaryDestination();

    Optional<RuntimeTrafficSplitDestination> effective =
        TrafficSplitFallbackResolver.resolveEffectiveDestination(
            config, nominal, bundle, selector, null, "test-route");

    assertTrue(effective.isPresent());
    assertEquals(STABLE_DEST, effective.get().destinationId());
    assertEquals(
        TrafficSplitFallbackResolver.FallbackReason.PRIMARY_UNAVAILABLE,
        TrafficSplitFallbackResolver.fallbackReason(config, nominal, effective.get()));
  }

  @Test
  void strictModeReturnsEmptyWhenCanaryTargetUnavailable() {
    ejectCanaryTarget();

    RuntimeTrafficSplitConfig config = fallbackConfig("STRICT");
    RuntimeTrafficSplitDestination nominal = canaryDestination();

    Optional<RuntimeTrafficSplitDestination> effective =
        TrafficSplitFallbackResolver.resolveEffectiveDestination(
            config, nominal, bundle, selector, null, "test-route");

    assertTrue(effective.isEmpty());
  }

  private void ejectCanaryTarget() {
    TargetKey canaryKey = new TargetKey(API_ID, CANARY_POOL, CANARY_TARGET);
    registry.recordFailure(canaryKey, FailureCategory.CONNECTION_REFUSED, HEALTH_POLICY, 1);
    registry.recordFailure(canaryKey, FailureCategory.CONNECTION_REFUSED, HEALTH_POLICY, 1);
  }

  private static RuntimeTrafficSplitConfig fallbackConfig(String fallbackMode) {
    return new RuntimeTrafficSplitConfig(
        POLICY_ID,
        "HEADER",
        "X-AutoAPI-Test-User",
        fallbackMode,
        "fp",
        100,
        List.of(stableDestination(), canaryDestination()));
  }

  private static RuntimeTrafficSplitDestination stableDestination() {
    return new RuntimeTrafficSplitDestination(
        STABLE_DEST,
        "stable",
        80,
        0,
        true,
        poolWithHealth(STABLE_POOL, STABLE_TARGET, "stable-v1"),
        0,
        80);
  }

  private static RuntimeTrafficSplitDestination canaryDestination() {
    return new RuntimeTrafficSplitDestination(
        CANARY_DEST,
        "canary",
        20,
        1,
        false,
        poolWithHealth(CANARY_POOL, CANARY_TARGET, "canary-v1"),
        80,
        100);
  }

  private static UpstreamConfig poolWithHealth(UUID poolId, UUID targetId, String host) {
    URI url = URI.create("http://" + host + ":8080");
    return UpstreamConfig.roundRobin(
        poolId,
        List.of(new UpstreamTargetReference(targetId, url, 1)),
        new com.autoapi.config.BackendHealthPolicyConfig(2, 30, 100));
  }
}
