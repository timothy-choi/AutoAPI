package com.autoapi.gateway.discovery;

import com.autoapi.config.RuntimeCircuitBreakerPolicyConfig;
import com.autoapi.config.RuntimeDiscoveredInstance;
import com.autoapi.config.RuntimeDiscoveredServiceConfig;
import com.autoapi.config.UpstreamTargetReference;
import com.autoapi.gateway.auth.AuthenticatedApiKey;
import com.autoapi.gateway.circuitbreaker.GatewayTargetSelector;
import com.autoapi.gateway.health.SelectedTarget;
import com.autoapi.middleware.RequestIdSupport;
import com.autoapi.proxy.GatewayAttributes;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.server.ServerWebExchange;

public final class DiscoveredServiceSelector {

  private final GatewayTargetSelector gatewayTargetSelector;
  private final ObjectProvider<GatewayDiscoveryMetrics> metricsProvider;

  public DiscoveredServiceSelector(
      GatewayTargetSelector gatewayTargetSelector,
      ObjectProvider<GatewayDiscoveryMetrics> metricsProvider) {
    this.gatewayTargetSelector = gatewayTargetSelector;
    this.metricsProvider = metricsProvider;
  }

  public SelectedTarget select(
      ServerWebExchange exchange,
      UUID apiId,
      RuntimeDiscoveredServiceConfig service,
      RuntimeCircuitBreakerPolicyConfig circuitPolicy,
      String routeId) {
    if (service == null || !service.hasEligibleInstances()) {
      recordNoEligible();
      throw new IllegalArgumentException("Discovered service has no eligible instances");
    }
    if ("CONSISTENT_HASH".equalsIgnoreCase(service.selectionStrategy())) {
      String hashMaterial = resolveHashMaterial(exchange, service);
      RuntimeDiscoveredInstance chosen =
          RendezvousHash.select(service, hashMaterial, service.instances());
      UpstreamTargetReference target =
          new UpstreamTargetReference(chosen.targetId(), chosen.url(), chosen.weight());
      recordSelection("CONSISTENT_HASH");
      return new SelectedTarget(target, false);
    }
    List<UpstreamTargetReference> targets = RendezvousHash.toTargetReferences(service.instances());
    SelectedTarget selected =
        gatewayTargetSelector.select(
            apiId, service.serviceId(), targets, null, circuitPolicy, routeId);
    recordSelection("ROUND_ROBIN");
    return selected;
  }

  public boolean hasEligibleInstance(
      UUID apiId,
      RuntimeDiscoveredServiceConfig service,
      RuntimeCircuitBreakerPolicyConfig circuitPolicy,
      String routeId) {
    if (service == null || !service.hasEligibleInstances()) {
      return false;
    }
    List<UpstreamTargetReference> targets = RendezvousHash.toTargetReferences(service.instances());
    return gatewayTargetSelector.hasCircuitEligibleTarget(
        apiId, service.serviceId(), targets, null, circuitPolicy, routeId);
  }

  private static String resolveHashMaterial(
      ServerWebExchange exchange, RuntimeDiscoveredServiceConfig service) {
    return switch (service.consistentHashKey()) {
      case "API_KEY_ID" -> {
        AuthenticatedApiKey apiKey = exchange.getAttribute(GatewayAttributes.AUTHENTICATED_API_KEY);
        yield apiKey != null && apiKey.keyId() != null && !apiKey.keyId().isBlank()
            ? apiKey.keyId()
            : RequestIdSupport.getRequestId(exchange);
      }
      case "HEADER" -> {
        String header =
            exchange.getRequest().getHeaders().getFirst(service.consistentHashKeyName());
        yield header == null || header.isBlank() ? RequestIdSupport.getRequestId(exchange) : header;
      }
      default -> RequestIdSupport.getRequestId(exchange);
    };
  }

  private void recordSelection(String strategy) {
    GatewayDiscoveryMetrics metrics = metricsProvider.getIfAvailable();
    if (metrics != null) {
      metrics.recordInstanceSelection(strategy);
    }
  }

  private void recordNoEligible() {
    GatewayDiscoveryMetrics metrics = metricsProvider.getIfAvailable();
    if (metrics != null) {
      metrics.recordNoEligibleInstance();
    }
  }
}
