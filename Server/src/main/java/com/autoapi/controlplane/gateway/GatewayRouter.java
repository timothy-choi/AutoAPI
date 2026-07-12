package com.autoapi.controlplane.gateway;

import com.autoapi.controlplane.api.ControlPlaneException;
import com.autoapi.controlplane.gateway.GatewayConfigStatusService.ConfigStatusRequest;
import com.autoapi.controlplane.gateway.GatewayHeartbeatService.ApiStatusSummary;
import com.autoapi.controlplane.gateway.GatewayHeartbeatService.HeartbeatResult;
import com.autoapi.controlplane.gateway.GatewayRegistrationService.RegistrationResult;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
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
public class GatewayRouter {

  @Bean
  @Order(6)
  RouterFunction<ServerResponse> gatewayManagementRoutes(
      GatewayRegistrationService registrationService,
      GatewayHeartbeatService heartbeatService,
      GatewayConfigStatusService configStatusService,
      GatewayQueryService queryService,
      ConvergenceService convergenceService) {
    Handler handler =
        new Handler(
            registrationService,
            heartbeatService,
            configStatusService,
            queryService,
            convergenceService);
    return RouterFunctions.route()
        .path(
            "/api/v1",
            builder ->
                builder
                    .POST("/gateways/register", handler::register)
                    .GET("/gateways", handler::listGateways)
                    .GET("/gateways/{gatewayId}", handler::getGateway)
                    .POST("/gateways/{gatewayId}/heartbeat", handler::heartbeat)
                    .POST("/gateways/{gatewayId}/config-status", handler::configStatus)
                    .GET("/apis/{apiId}/convergence", handler::convergence)
                    .GET("/apis/{apiId}/activation-events", handler::activationEvents))
        .build();
  }

  static final class Handler {

    private static final Logger log = LoggerFactory.getLogger(Handler.class);

    private final GatewayRegistrationService registrationService;
    private final GatewayHeartbeatService heartbeatService;
    private final GatewayConfigStatusService configStatusService;
    private final GatewayQueryService queryService;
    private final ConvergenceService convergenceService;

    Handler(
        GatewayRegistrationService registrationService,
        GatewayHeartbeatService heartbeatService,
        GatewayConfigStatusService configStatusService,
        GatewayQueryService queryService,
        ConvergenceService convergenceService) {
      this.registrationService = registrationService;
      this.heartbeatService = heartbeatService;
      this.configStatusService = configStatusService;
      this.queryService = queryService;
      this.convergenceService = convergenceService;
    }

    Mono<ServerResponse> register(ServerRequest request) {
      return request
          .bodyToMono(RegisterGatewayRequest.class)
          .flatMap(
              body ->
                  registrationService.register(
                      body.gatewayId(),
                      body.gatewayGroup(),
                      body.softwareVersion(),
                      body.startedAt(),
                      body.metadata()))
          .flatMap(
              result ->
                  (result.created()
                          ? ServerResponse.status(HttpStatus.CREATED)
                          : ServerResponse.ok())
                      .bodyValue(RegisterGatewayResponse.from(result)))
          .onErrorResume(ControlPlaneException.class, this::error)
          .onErrorResume(ex -> unexpectedError(request, ex));
    }

    Mono<ServerResponse> listGateways(ServerRequest request) {
      String group = request.queryParam("gatewayGroup").orElse(null);
      String liveness = request.queryParam("liveness").orElse(null);
      return queryService
          .listGateways(group, liveness)
          .collectList()
          .flatMap(list -> ServerResponse.ok().bodyValue(list))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> getGateway(ServerRequest request) {
      return queryService
          .getGateway(request.pathVariable("gatewayId"))
          .flatMap(detail -> ServerResponse.ok().bodyValue(detail))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> heartbeat(ServerRequest request) {
      String gatewayId = request.pathVariable("gatewayId");
      return request
          .bodyToMono(HeartbeatRequest.class)
          .flatMap(
              body -> {
                List<ApiStatusSummary> summaries =
                    body.apiStatuses() == null
                        ? List.of()
                        : body.apiStatuses().stream()
                            .map(
                                item ->
                                    new ApiStatusSummary(
                                        item.apiId(),
                                        item.activeVersion(),
                                        item.activeContentHash()))
                            .toList();
                return heartbeatService.heartbeat(gatewayId, body.sentAt(), summaries);
              })
          .flatMap(result -> ServerResponse.ok().bodyValue(HeartbeatResponse.from(result)))
          .onErrorResume(ControlPlaneException.class, this::error)
          .onErrorResume(ex -> unexpectedError(request, ex));
    }

    Mono<ServerResponse> configStatus(ServerRequest request) {
      String gatewayId = request.pathVariable("gatewayId");
      return request
          .bodyToMono(ConfigStatusBody.class)
          .flatMap(
              body ->
                  configStatusService.report(
                      gatewayId,
                      new ConfigStatusRequest(
                          body.reportId(),
                          body.apiId(),
                          body.version(),
                          body.contentHash(),
                          body.status(),
                          body.errorCode(),
                          body.diagnostic(),
                          body.applyDurationMs())))
          .flatMap(
              result ->
                  ServerResponse.status(HttpStatus.ACCEPTED)
                      .bodyValue(Map.of("accepted", true, "idempotent", result.idempotent())))
          .onErrorResume(ControlPlaneException.class, this::error)
          .onErrorResume(ex -> unexpectedError(request, ex));
    }

    Mono<ServerResponse> convergence(ServerRequest request) {
      UUID apiId = UUID.fromString(request.pathVariable("apiId"));
      return convergenceService
          .getConvergence(apiId)
          .flatMap(response -> ServerResponse.ok().bodyValue(response))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> activationEvents(ServerRequest request) {
      UUID apiId = UUID.fromString(request.pathVariable("apiId"));
      Integer limit = request.queryParam("limit").map(Integer::parseInt).orElse(null);
      return queryService
          .listActivationEvents(apiId, limit)
          .collectList()
          .flatMap(events -> ServerResponse.ok().bodyValue(events))
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

    private Mono<ServerResponse> unexpectedError(ServerRequest request, Throwable ex) {
      log.error(
          "Gateway management request failed method={} path={} errorClass={} message={}",
          request.method(),
          request.path(),
          ex.getClass().getName(),
          ex.getMessage(),
          ex);
      return error(ControlPlaneException.internal("An internal control plane error occurred"));
    }
  }

  record RegisterGatewayRequest(
      String gatewayId,
      String gatewayGroup,
      String softwareVersion,
      OffsetDateTime startedAt,
      Map<String, Object> metadata) {}

  record RegisterGatewayResponse(
      String gatewayId,
      String gatewayGroup,
      String softwareVersion,
      String registeredAt,
      String lastSeenAt) {

    static RegisterGatewayResponse from(RegistrationResult result) {
      return new RegisterGatewayResponse(
          result.gatewayId(),
          result.gatewayGroup(),
          result.softwareVersion(),
          result.registeredAt().toString(),
          result.lastSeenAt().toString());
    }
  }

  record HeartbeatRequest(OffsetDateTime sentAt, List<HeartbeatApiStatus> apiStatuses) {}

  record HeartbeatApiStatus(UUID apiId, long activeVersion, String activeContentHash) {}

  record HeartbeatResponse(String gatewayId, String receivedAt, int nextHeartbeatSeconds) {

    static HeartbeatResponse from(HeartbeatResult result) {
      return new HeartbeatResponse(
          result.gatewayId(), result.receivedAt().toString(), result.nextHeartbeatSeconds());
    }
  }

  record ConfigStatusBody(
      UUID reportId,
      UUID apiId,
      long version,
      String contentHash,
      String status,
      String errorCode,
      String diagnostic,
      Long applyDurationMs) {}
}
