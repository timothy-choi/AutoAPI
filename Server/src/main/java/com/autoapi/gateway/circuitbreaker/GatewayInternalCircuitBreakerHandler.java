package com.autoapi.gateway.circuitbreaker;

import com.autoapi.gateway.GatewayProperties;
import com.autoapi.gateway.health.TargetKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

public class GatewayInternalCircuitBreakerHandler {

  private final CircuitBreakerRegistry circuitBreakerRegistry;
  private final String gatewayId;

  public GatewayInternalCircuitBreakerHandler(
      CircuitBreakerRegistry circuitBreakerRegistry, GatewayProperties gatewayProperties) {
    this.circuitBreakerRegistry = circuitBreakerRegistry;
    this.gatewayId =
        gatewayProperties.gatewayId() == null ? "unknown" : gatewayProperties.gatewayId();
  }

  public Mono<ServerResponse> circuitBreakers(ServerRequest request) {
    Map<TargetKey, CircuitBreakerState> states = circuitBreakerRegistry.snapshotStates();
    List<TargetCircuitBreakerStatus> targets = new ArrayList<>();
    states.forEach(
        (key, state) ->
            targets.add(
                new TargetCircuitBreakerStatus(
                    key.apiId().toString(),
                    key.poolId().toString(),
                    key.targetId().toString(),
                    state.name())));
    CircuitBreakerStatusResponse response =
        new CircuitBreakerStatusResponse(gatewayId, List.copyOf(targets));
    return ServerResponse.ok().bodyValue(response);
  }

  record TargetCircuitBreakerStatus(String apiId, String poolId, String targetId, String state) {}

  record CircuitBreakerStatusResponse(String gatewayId, List<TargetCircuitBreakerStatus> targets) {}
}
