package com.autoapi.gateway.traffic;

import com.autoapi.config.RuntimeCircuitBreakerPolicyConfig;
import com.autoapi.config.RuntimeTrafficSplitConfig;
import com.autoapi.config.RuntimeTrafficSplitDestination;
import com.autoapi.config.UpstreamConfig;
import com.autoapi.config.UpstreamTargetReference;
import com.autoapi.gateway.circuitbreaker.GatewayTargetSelector;
import com.autoapi.gateway.config.ActiveRuntimeBundle;
import com.autoapi.gateway.health.PassiveHealthPolicy;
import com.autoapi.gateway.health.SelectedTarget;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class TrafficSplitFallbackResolver {

  public enum FallbackReason {
    NONE,
    PRIMARY_UNAVAILABLE,
    ANY_HEALTHY_UNAVAILABLE
  }

  private TrafficSplitFallbackResolver() {}

  public static Optional<RuntimeTrafficSplitDestination> resolveEffectiveDestination(
      RuntimeTrafficSplitConfig config,
      RuntimeTrafficSplitDestination nominal,
      ActiveRuntimeBundle bundle,
      GatewayTargetSelector targetSelector,
      RuntimeCircuitBreakerPolicyConfig circuitPolicy,
      String routeId) {
    if (hasEligibleTarget(bundle, targetSelector, nominal, circuitPolicy, routeId)) {
      return Optional.of(nominal);
    }
    return switch (config.fallbackMode()) {
      case "STRICT" -> Optional.empty();
      case "FALLBACK_TO_PRIMARY" ->
          findPrimary(config, bundle, targetSelector, circuitPolicy, routeId);
      case "FALLBACK_TO_ANY_HEALTHY_SPLIT" ->
          findAnyHealthy(config, nominal, bundle, targetSelector, circuitPolicy, routeId);
      default -> Optional.empty();
    };
  }

  public static FallbackReason fallbackReason(
      RuntimeTrafficSplitConfig config,
      RuntimeTrafficSplitDestination nominal,
      RuntimeTrafficSplitDestination effective) {
    if (effective.destinationId().equals(nominal.destinationId())) {
      return FallbackReason.NONE;
    }
    if ("FALLBACK_TO_PRIMARY".equals(config.fallbackMode())) {
      return FallbackReason.PRIMARY_UNAVAILABLE;
    }
    return FallbackReason.ANY_HEALTHY_UNAVAILABLE;
  }

  private static Optional<RuntimeTrafficSplitDestination> findPrimary(
      RuntimeTrafficSplitConfig config,
      ActiveRuntimeBundle bundle,
      GatewayTargetSelector targetSelector,
      RuntimeCircuitBreakerPolicyConfig circuitPolicy,
      String routeId) {
    return config.destinations().stream()
        .filter(RuntimeTrafficSplitDestination::primary)
        .filter(
            destination ->
                hasEligibleTarget(bundle, targetSelector, destination, circuitPolicy, routeId))
        .findFirst();
  }

  private static Optional<RuntimeTrafficSplitDestination> findAnyHealthy(
      RuntimeTrafficSplitConfig config,
      RuntimeTrafficSplitDestination nominal,
      ActiveRuntimeBundle bundle,
      GatewayTargetSelector targetSelector,
      RuntimeCircuitBreakerPolicyConfig circuitPolicy,
      String routeId) {
    return config.destinations().stream()
        .filter(destination -> !destination.destinationId().equals(nominal.destinationId()))
        .sorted(
            Comparator.comparing(RuntimeTrafficSplitDestination::priority)
                .thenComparing(RuntimeTrafficSplitDestination::destinationId))
        .filter(
            destination ->
                hasEligibleTarget(bundle, targetSelector, destination, circuitPolicy, routeId))
        .findFirst();
  }

  private static boolean hasEligibleTarget(
      ActiveRuntimeBundle bundle,
      GatewayTargetSelector targetSelector,
      RuntimeTrafficSplitDestination destination,
      RuntimeCircuitBreakerPolicyConfig circuitPolicy,
      String routeId) {
    UpstreamConfig upstream = destination.upstreamPool();
    List<UpstreamTargetReference> targets = upstream.targets();
    if (targets.isEmpty()) {
      return false;
    }
    PassiveHealthPolicy policy =
        upstream.backendHealth() != null
            ? PassiveHealthPolicy.from(upstream.backendHealth())
            : null;
    try {
      SelectedTarget selected =
          targetSelector.select(
              bundle.apiId(), upstream.poolId(), targets, policy, circuitPolicy, routeId);
      return selected != null && selected.target() != null && !selected.forcedSelection();
    } catch (IllegalArgumentException
        | com.autoapi.gateway.circuitbreaker.CircuitBreakerOpenException ex) {
      return false;
    }
  }
}
