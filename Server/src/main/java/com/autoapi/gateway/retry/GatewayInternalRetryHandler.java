package com.autoapi.gateway.retry;

import com.autoapi.gateway.GatewayProperties;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/** Internal gateway-local retry budget visibility. Unauthenticated; trusted-network only. */
@Component
public class GatewayInternalRetryHandler {

  private final RetryBudgetRegistry registry;
  private final GatewayProperties gatewayProperties;

  public GatewayInternalRetryHandler(
      RetryBudgetRegistry registry, GatewayProperties gatewayProperties) {
    this.registry = registry;
    this.gatewayProperties = gatewayProperties;
  }

  public Mono<ServerResponse> retryStatus(ServerRequest request) {
    List<Map<String, Object>> budgets = new ArrayList<>();
    for (RetryBudgetRegistry.BudgetView view : registry.activeBudgetViews()) {
      Map<String, Object> entry = new LinkedHashMap<>();
      entry.put("apiId", view.apiId().toString());
      entry.put("routeId", view.routeId());
      entry.put("policyId", view.policyId().toString());
      entry.put("windowSeconds", view.windowSeconds());
      entry.put("originalRequests", view.originalRequests());
      entry.put("retriesUsed", view.retriesUsed());
      entry.put("retryCapacity", view.retryCapacity());
      budgets.add(entry);
    }
    return ServerResponse.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            Map.of(
                "gatewayId",
                gatewayProperties.gatewayId() == null ? "unknown" : gatewayProperties.gatewayId(),
                "budgets",
                budgets));
  }
}
