package com.autoapi.controlplane.observability;

import com.autoapi.controlplane.api.ControlPlaneException;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Configuration
@ConditionalOnProperty(
    name = "autoapi.controlplane.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class ObservabilityManagementRouter {

  @Bean
  @Order(12)
  RouterFunction<ServerResponse> observabilityManagementRoutes(
      GatewayInstanceService gatewayInstanceService, RequestSummaryService requestSummaryService) {
    Handler handler = new Handler(gatewayInstanceService, requestSummaryService);
    return RouterFunctions.route()
        .path(
            "/api/v1/management",
            builder ->
                builder
                    .GET("/gateways", handler::listGateways)
                    .GET("/gateways/{gatewayId}", handler::getGateway)
                    .GET("/gateways/{gatewayId}/instances", handler::listInstances)
                    .GET("/observability/requests", handler::listRequestSummaries))
        .build();
  }

  static final class Handler {

    private final GatewayInstanceService gatewayInstanceService;
    private final RequestSummaryService requestSummaryService;

    Handler(
        GatewayInstanceService gatewayInstanceService,
        RequestSummaryService requestSummaryService) {
      this.gatewayInstanceService = gatewayInstanceService;
      this.requestSummaryService = requestSummaryService;
    }

    Mono<ServerResponse> listGateways(ServerRequest request) {
      String gatewayId = request.queryParam("gatewayId").orElse(null);
      String status = request.queryParam("status").orElse(null);
      Long snapshotVersion =
          request.queryParam("activeSnapshotVersion").map(Long::parseLong).orElse(null);
      return gatewayInstanceService
          .listManagedGateways(gatewayId, status, snapshotVersion)
          .collectList()
          .flatMap(list -> ServerResponse.ok().bodyValue(list))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> getGateway(ServerRequest request) {
      return gatewayInstanceService
          .getManagedGateway(request.pathVariable("gatewayId"))
          .flatMap(detail -> ServerResponse.ok().bodyValue(detail))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> listInstances(ServerRequest request) {
      String status = request.queryParam("status").orElse(null);
      return gatewayInstanceService
          .listInstances(request.pathVariable("gatewayId"), status)
          .collectList()
          .flatMap(list -> ServerResponse.ok().bodyValue(list))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> listRequestSummaries(ServerRequest request) {
      int limit = request.queryParam("limit").map(Integer::parseInt).orElse(50);
      String gatewayId = request.queryParam("gatewayId").orElse(null);
      return requestSummaryService
          .listSummaries(gatewayId, limit)
          .collectList()
          .flatMap(list -> ServerResponse.ok().bodyValue(list))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    private Mono<ServerResponse> error(ControlPlaneException ex) {
      return ServerResponse.status(ex.status())
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(
              Map.of(
                  "error",
                  Map.of(
                      "code", ex.code(),
                      "message", ex.getMessage(),
                      "details", ex.validationErrors())));
    }
  }
}
