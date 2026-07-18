package com.autoapi.gateway.traffic;

import com.autoapi.config.RouteConfig;
import com.autoapi.config.RuntimeTrafficSplitConfig;
import com.autoapi.config.RuntimeTrafficSplitDestination;
import com.autoapi.gateway.circuitbreaker.GatewayTargetSelector;
import com.autoapi.gateway.config.ActiveRuntimeBundle;
import com.autoapi.middleware.RequestIdSupport;
import com.autoapi.proxy.GatewayAttributes;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.server.ServerWebExchange;

public class TrafficSplitSelector {

  private static final Logger log = LoggerFactory.getLogger(TrafficSplitSelector.class);

  private final GatewayTargetSelector targetSelector;
  private final TrafficSplitRegistry registry;
  private final ObjectProvider<GatewayTrafficSplitMetrics> metricsProvider;

  public TrafficSplitSelector(
      GatewayTargetSelector targetSelector,
      TrafficSplitRegistry registry,
      ObjectProvider<GatewayTrafficSplitMetrics> metricsProvider) {
    this.targetSelector = targetSelector;
    this.registry = registry;
    this.metricsProvider = metricsProvider;
  }

  public Optional<TrafficSplitDecision> select(
      ServerWebExchange exchange, ActiveRuntimeBundle bundle, RouteConfig route) {
    RuntimeTrafficSplitConfig config = route.trafficSplit();
    if (config == null) {
      return Optional.empty();
    }
    TrafficSelectionKeyResolver.ResolvedSelectionKey selectionKey =
        TrafficSelectionKeyResolver.resolve(config, exchange);
    Optional<RuntimeTrafficSplitDestination> nominal =
        WeightedDestinationSelector.selectNominalDestination(
            config, route.id(), selectionKey.value());
    if (nominal.isEmpty()) {
      registry.recordUnavailable(route.id(), config.policyId());
      recordUnavailableMetrics(route, config);
      return Optional.empty();
    }
    RuntimeTrafficSplitDestination nominalDestination = nominal.get();
    Optional<RuntimeTrafficSplitDestination> effective =
        TrafficSplitFallbackResolver.resolveEffectiveDestination(
            config, nominalDestination, bundle, targetSelector, route.circuitBreaker(), route.id());
    if (effective.isEmpty()) {
      registry.recordUnavailable(route.id(), config.policyId());
      recordUnavailableMetrics(route, config);
      return Optional.empty();
    }
    RuntimeTrafficSplitDestination effectiveDestination = effective.get();
    TrafficSplitFallbackResolver.FallbackReason fallbackReason =
        TrafficSplitFallbackResolver.fallbackReason(
            config, nominalDestination, effectiveDestination);
    boolean fallbackUsed = fallbackReason != TrafficSplitFallbackResolver.FallbackReason.NONE;
    String material =
        StableTrafficHash.hashMaterial(
            route.id(), config.policyId().toString(), config.fingerprint(), selectionKey.value());
    long bucket = StableTrafficHash.nonNegativeBucket(material);
    int selectedBucket = StableTrafficHash.bucketModTotal(bucket, config.totalWeight());
    registry.recordAssignment(
        route.id(),
        config.policyId(),
        effectiveDestination.destinationId(),
        effectiveDestination.name(),
        fallbackUsed);
    recordAssignmentMetrics(route, config, effectiveDestination, fallbackUsed, fallbackReason);
    logSelection(
        exchange,
        route,
        config,
        effectiveDestination,
        selectionKey,
        selectedBucket,
        fallbackUsed,
        fallbackReason,
        nominalDestination);
    TrafficSplitDecision decision =
        new TrafficSplitDecision(
            config.policyId(),
            effectiveDestination.destinationId(),
            effectiveDestination.name(),
            effectiveDestination.upstreamPool(),
            selectionKey.source().name(),
            selectedBucket,
            config.totalWeight(),
            nominalDestination.destinationId(),
            nominalDestination.name(),
            fallbackUsed,
            fallbackReason);
    exchange.getAttributes().put(GatewayAttributes.TRAFFIC_SPLIT_DECISION, decision);
    return Optional.of(decision);
  }

  private void logSelection(
      ServerWebExchange exchange,
      RouteConfig route,
      RuntimeTrafficSplitConfig config,
      RuntimeTrafficSplitDestination effectiveDestination,
      TrafficSelectionKeyResolver.ResolvedSelectionKey selectionKey,
      int selectedBucket,
      boolean fallbackUsed,
      TrafficSplitFallbackResolver.FallbackReason fallbackReason,
      RuntimeTrafficSplitDestination nominalDestination) {
    String requestId = RequestIdSupport.getRequestId(exchange);
    if (fallbackUsed) {
      log.info(
          "requestId={} routeId={} policyId={} selectedDestinationId={} fallbackDestinationId={}"
              + " fallbackReason={} fallbackMode={}",
          requestId,
          route.id(),
          config.policyId(),
          nominalDestination.destinationId(),
          effectiveDestination.destinationId(),
          fallbackReason.name(),
          config.fallbackMode());
    } else if (log.isDebugEnabled()) {
      log.debug(
          "requestId={} routeId={} policyId={} destinationId={} destinationName={} bucket={}"
              + " totalWeight={} selectionKeySource={}",
          requestId,
          route.id(),
          config.policyId(),
          effectiveDestination.destinationId(),
          effectiveDestination.name(),
          selectedBucket,
          config.totalWeight(),
          selectionKey.source().name());
    }
  }

  private void recordAssignmentMetrics(
      RouteConfig route,
      RuntimeTrafficSplitConfig config,
      RuntimeTrafficSplitDestination destination,
      boolean fallbackUsed,
      TrafficSplitFallbackResolver.FallbackReason fallbackReason) {
    GatewayTrafficSplitMetrics metrics = metricsProvider.getIfAvailable();
    if (metrics == null) {
      return;
    }
    metrics.recordAssignment(
        route.id(),
        config.policyId().toString(),
        destination.destinationId().toString(),
        destination.name(),
        config.fallbackMode());
    if (fallbackUsed) {
      metrics.recordFallback(
          route.id(),
          config.policyId().toString(),
          destination.destinationId().toString(),
          destination.name(),
          config.fallbackMode(),
          fallbackReason.name());
    }
  }

  private void recordUnavailableMetrics(RouteConfig route, RuntimeTrafficSplitConfig config) {
    GatewayTrafficSplitMetrics metrics = metricsProvider.getIfAvailable();
    if (metrics != null) {
      metrics.recordUnavailable(route.id(), config.policyId().toString(), config.fallbackMode());
    }
  }
}
