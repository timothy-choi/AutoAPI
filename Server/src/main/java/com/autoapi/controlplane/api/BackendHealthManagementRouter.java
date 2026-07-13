package com.autoapi.controlplane.api;

import com.autoapi.controlplane.backendhealth.BackendHealthPolicyService;
import com.autoapi.controlplane.persistence.BackendHealthPolicyEntity;
import com.autoapi.controlplane.upstream.UpstreamService;
import java.util.Map;
import java.util.UUID;
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
public class BackendHealthManagementRouter {

  @Bean
  @Order(8)
  RouterFunction<ServerResponse> backendHealthRoutes(
      BackendHealthPolicyService backendHealthPolicyService, UpstreamService upstreamService) {
    Handler handler = new Handler(backendHealthPolicyService, upstreamService);
    return RouterFunctions.route()
        .path(
            "/api/v1",
            builder ->
                builder
                    .POST("/apis/{apiId}/backend-health-policies", handler::createPolicy)
                    .GET("/apis/{apiId}/backend-health-policies", handler::listPolicies)
                    .GET("/apis/{apiId}/backend-health-policies/{policyId}", handler::getPolicy)
                    .PATCH("/apis/{apiId}/backend-health-policies/{policyId}", handler::patchPolicy)
                    .PUT("/upstream-pools/{poolId}/backend-health-policy", handler::bindPolicy)
                    .DELETE(
                        "/upstream-pools/{poolId}/backend-health-policy", handler::unbindPolicy))
        .build();
  }

  static final class Handler {

    private final BackendHealthPolicyService backendHealthPolicyService;
    private final UpstreamService upstreamService;

    Handler(
        BackendHealthPolicyService backendHealthPolicyService, UpstreamService upstreamService) {
      this.backendHealthPolicyService = backendHealthPolicyService;
      this.upstreamService = upstreamService;
    }

    Mono<ServerResponse> createPolicy(ServerRequest request) {
      UUID apiId = uuid(request, "apiId");
      return request
          .bodyToMono(CreateBackendHealthPolicyRequest.class)
          .flatMap(
              body ->
                  backendHealthPolicyService.create(
                      apiId,
                      body.name(),
                      body.consecutiveFailureThreshold(),
                      body.ejectionDurationSeconds(),
                      body.maxEjectionPercent(),
                      body.enabled()))
          .flatMap(
              entity ->
                  ServerResponse.status(HttpStatus.CREATED)
                      .bodyValue(BackendHealthPolicyResponse.from(entity)))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> listPolicies(ServerRequest request) {
      UUID apiId = uuid(request, "apiId");
      return backendHealthPolicyService
          .list(apiId)
          .map(BackendHealthPolicyResponse::from)
          .collectList()
          .flatMap(list -> ServerResponse.ok().bodyValue(list))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> getPolicy(ServerRequest request) {
      UUID apiId = uuid(request, "apiId");
      UUID policyId = uuid(request, "policyId");
      return backendHealthPolicyService
          .get(apiId, policyId)
          .flatMap(
              entity -> ServerResponse.ok().bodyValue(BackendHealthPolicyResponse.from(entity)))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> patchPolicy(ServerRequest request) {
      UUID apiId = uuid(request, "apiId");
      UUID policyId = uuid(request, "policyId");
      return request
          .bodyToMono(PatchBackendHealthPolicyRequest.class)
          .flatMap(
              body ->
                  backendHealthPolicyService.patch(
                      apiId,
                      policyId,
                      body.consecutiveFailureThreshold(),
                      body.ejectionDurationSeconds(),
                      body.maxEjectionPercent(),
                      body.enabled()))
          .flatMap(
              entity -> ServerResponse.ok().bodyValue(BackendHealthPolicyResponse.from(entity)))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> bindPolicy(ServerRequest request) {
      UUID poolId = uuid(request, "poolId");
      return request
          .bodyToMono(BindBackendHealthPolicyRequest.class)
          .flatMap(
              body -> upstreamService.bindBackendHealthPolicy(poolId, body.backendHealthPolicyId()))
          .flatMap(
              entity ->
                  ServerResponse.ok().bodyValue(UpstreamPoolHealthBindingResponse.from(entity)))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> unbindPolicy(ServerRequest request) {
      UUID poolId = uuid(request, "poolId");
      return upstreamService
          .clearBackendHealthPolicy(poolId)
          .flatMap(
              entity ->
                  ServerResponse.ok().bodyValue(UpstreamPoolHealthBindingResponse.from(entity)))
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

    private static UUID uuid(ServerRequest request, String name) {
      return UUID.fromString(request.pathVariable(name));
    }
  }

  record CreateBackendHealthPolicyRequest(
      String name,
      int consecutiveFailureThreshold,
      int ejectionDurationSeconds,
      int maxEjectionPercent,
      boolean enabled) {}

  record PatchBackendHealthPolicyRequest(
      Integer consecutiveFailureThreshold,
      Integer ejectionDurationSeconds,
      Integer maxEjectionPercent,
      Boolean enabled) {}

  record BindBackendHealthPolicyRequest(UUID backendHealthPolicyId) {}

  record BackendHealthPolicyResponse(
      UUID id,
      UUID apiId,
      String name,
      int consecutiveFailureThreshold,
      int ejectionDurationSeconds,
      int maxEjectionPercent,
      boolean enabled,
      String createdAt,
      String updatedAt) {

    static BackendHealthPolicyResponse from(BackendHealthPolicyEntity entity) {
      return new BackendHealthPolicyResponse(
          entity.id(),
          entity.apiId(),
          entity.name(),
          entity.consecutiveFailureThreshold(),
          entity.ejectionDurationSeconds(),
          entity.maxEjectionPercent(),
          entity.enabled(),
          entity.createdAt().toString(),
          entity.updatedAt().toString());
    }
  }

  record UpstreamPoolHealthBindingResponse(UUID poolId, UUID backendHealthPolicyId) {

    static UpstreamPoolHealthBindingResponse from(
        com.autoapi.controlplane.persistence.UpstreamPoolEntity entity) {
      return new UpstreamPoolHealthBindingResponse(entity.id(), entity.backendHealthPolicyId());
    }
  }
}
