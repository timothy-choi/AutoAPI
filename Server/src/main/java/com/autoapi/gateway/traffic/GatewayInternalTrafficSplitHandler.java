package com.autoapi.gateway.traffic;

import com.autoapi.config.RouteConfig;
import com.autoapi.config.RuntimeConfig;
import com.autoapi.config.RuntimeTrafficSplitConfig;
import com.autoapi.config.RuntimeTrafficSplitDestination;
import com.autoapi.gateway.GatewayProperties;
import com.autoapi.gateway.config.ActiveRuntimeBundle;
import com.autoapi.gateway.config.ActiveRuntimeConfigHolder;
import com.autoapi.runtime.AutoApiRole;
import com.autoapi.runtime.ConditionalOnAutoApiRole;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/** Internal gateway-local traffic-split visibility. Unauthenticated; trusted-network only. */
@Component
@ConditionalOnAutoApiRole({AutoApiRole.GATEWAY, AutoApiRole.COMBINED})
public class GatewayInternalTrafficSplitHandler {

  private final ActiveRuntimeConfigHolder activeRuntimeConfigHolder;
  private final TrafficSplitRegistry registry;
  private final GatewayProperties gatewayProperties;

  public GatewayInternalTrafficSplitHandler(
      ActiveRuntimeConfigHolder activeRuntimeConfigHolder,
      TrafficSplitRegistry registry,
      GatewayProperties gatewayProperties) {
    this.activeRuntimeConfigHolder = activeRuntimeConfigHolder;
    this.registry = registry;
    this.gatewayProperties = gatewayProperties;
  }

  public Mono<ServerResponse> trafficSplits(ServerRequest request) {
    ActiveRuntimeBundle bundle = activeRuntimeConfigHolder.getActiveForRequest();
    if (bundle == null) {
      return ServerResponse.status(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(Map.of("error", Map.of("code", "GATEWAY_NOT_READY")));
    }

    Map<TrafficSplitRegistry.CounterKey, TrafficSplitRegistry.CounterState> counters =
        registry.snapshot();
    RuntimeConfig config = bundle.runtimeConfig();
    List<Map<String, Object>> policies = new ArrayList<>();
    for (RouteConfig route : config.routes()) {
      RuntimeTrafficSplitConfig split = route.trafficSplit();
      if (split == null) {
        continue;
      }
      List<Map<String, Object>> destinationViews = new ArrayList<>();
      for (RuntimeTrafficSplitDestination destination : split.destinations()) {
        TrafficSplitRegistry.CounterKey key =
            new TrafficSplitRegistry.CounterKey(
                route.id(), split.policyId(), destination.destinationId());
        TrafficSplitRegistry.CounterState state = counters.get(key);
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("destinationId", destination.destinationId().toString());
        view.put("name", destination.name());
        view.put("weight", destination.weight());
        view.put("primary", destination.primary());
        view.put("assignedRequests", state == null ? 0L : state.assignedRequests());
        view.put("fallbackRequests", state == null ? 0L : state.fallbackRequests());
        destinationViews.add(view);
      }
      Map<String, Object> policyView = new LinkedHashMap<>();
      policyView.put("apiId", bundle.apiId().toString());
      policyView.put("routeId", route.id());
      policyView.put("policyId", split.policyId().toString());
      policyView.put("selectionKey", split.selectionKey());
      policyView.put("fallbackMode", split.fallbackMode());
      policyView.put("fingerprint", split.fingerprint());
      policyView.put("destinations", destinationViews);
      policies.add(policyView);
    }

    String gatewayId =
        gatewayProperties.gatewayId() == null ? "unknown" : gatewayProperties.gatewayId();
    return ServerResponse.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(Map.of("gatewayId", gatewayId, "policies", policies));
  }
}
