package com.autoapi.gateway.observability;

import com.autoapi.runtime.AutoApiRole;
import com.autoapi.runtime.ConditionalOnAutoApiRole;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
@ConditionalOnAutoApiRole({AutoApiRole.GATEWAY, AutoApiRole.COMBINED})
public class GatewayInternalObservabilityHandler {

  private final GatewayRequestSummaryBuffer summaryBuffer;
  private final GatewayInstanceIdentity identity;

  public GatewayInternalObservabilityHandler(
      GatewayRequestSummaryBuffer summaryBuffer, GatewayInstanceIdentity identity) {
    this.summaryBuffer = summaryBuffer;
    this.identity = identity;
  }

  public Mono<ServerResponse> requestSummaries(ServerRequest request) {
    int limit = request.queryParam("limit").map(Integer::parseInt).orElse(20);
    List<GatewayRequestSummary> summaries = summaryBuffer.recent(limit);
    return ServerResponse.ok()
        .bodyValue(
            java.util.Map.of(
                "gatewayId",
                identity.gatewayId(),
                "instanceId",
                identity.instanceId(),
                "summaries",
                summaries));
  }
}
