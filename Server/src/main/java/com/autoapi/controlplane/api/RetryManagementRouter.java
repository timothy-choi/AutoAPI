package com.autoapi.controlplane.api;

import com.autoapi.controlplane.persistence.RetryPolicyEntity;
import com.autoapi.controlplane.persistence.RoutePolicyBindingEntity;
import com.autoapi.controlplane.retry.RetryPolicyService;
import com.autoapi.controlplane.retry.RetryRouteBindingService;
import com.fasterxml.jackson.annotation.JsonInclude;
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
public class RetryManagementRouter {

  @Bean
  @Order(9)
  RouterFunction<ServerResponse> retryRoutes(
      RetryPolicyService retryPolicyService, RetryRouteBindingService retryRouteBindingService) {
    Handler handler = new Handler(retryPolicyService, retryRouteBindingService);
    return RouterFunctions.route()
        .path(
            "/api/v1",
            builder ->
                builder
                    .POST("/apis/{apiId}/retry-policies", handler::createPolicy)
                    .GET("/apis/{apiId}/retry-policies", handler::listPolicies)
                    .GET("/apis/{apiId}/retry-policies/{policyId}", handler::getPolicy)
                    .PATCH("/apis/{apiId}/retry-policies/{policyId}", handler::patchPolicy)
                    .PUT("/routes/{routeId}/retry-policy", handler::bindPolicy)
                    .DELETE("/routes/{routeId}/retry-policy", handler::unbindPolicy))
        .build();
  }

  static final class Handler {

    private final RetryPolicyService retryPolicyService;
    private final RetryRouteBindingService retryRouteBindingService;

    Handler(
        RetryPolicyService retryPolicyService, RetryRouteBindingService retryRouteBindingService) {
      this.retryPolicyService = retryPolicyService;
      this.retryRouteBindingService = retryRouteBindingService;
    }

    Mono<ServerResponse> createPolicy(ServerRequest request) {
      UUID apiId = uuid(request, "apiId");
      return request
          .bodyToMono(CreateRetryPolicyRequest.class)
          .flatMap(
              body ->
                  retryPolicyService.create(
                      apiId,
                      body.name(),
                      body.maxAttempts(),
                      body.perAttemptTimeoutMs(),
                      body.retryOnConnectFailure(),
                      body.retryOnConnectionReset(),
                      body.retryOnDnsFailure(),
                      body.retryOnResponseTimeout(),
                      body.retryableMethods(),
                      body.requireIdempotencyKeyForUnsafeMethods(),
                      body.budgetPercent(),
                      body.budgetMinRetriesPerSecond(),
                      body.budgetWindowSeconds(),
                      body.enabled()))
          .flatMap(
              entity ->
                  ServerResponse.status(HttpStatus.CREATED)
                      .bodyValue(RetryPolicyResponse.from(entity)))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> listPolicies(ServerRequest request) {
      UUID apiId = uuid(request, "apiId");
      return retryPolicyService
          .list(apiId)
          .map(RetryPolicyResponse::from)
          .collectList()
          .flatMap(list -> ServerResponse.ok().bodyValue(list))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> getPolicy(ServerRequest request) {
      UUID apiId = uuid(request, "apiId");
      UUID policyId = uuid(request, "policyId");
      return retryPolicyService
          .get(apiId, policyId)
          .flatMap(entity -> ServerResponse.ok().bodyValue(RetryPolicyResponse.from(entity)))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> patchPolicy(ServerRequest request) {
      UUID apiId = uuid(request, "apiId");
      UUID policyId = uuid(request, "policyId");
      return request
          .bodyToMono(PatchRetryPolicyRequest.class)
          .flatMap(
              body ->
                  retryPolicyService.patch(
                      apiId,
                      policyId,
                      body.maxAttempts(),
                      body.perAttemptTimeoutMs(),
                      body.retryOnConnectFailure(),
                      body.retryOnConnectionReset(),
                      body.retryOnDnsFailure(),
                      body.retryOnResponseTimeout(),
                      body.retryableMethods(),
                      body.requireIdempotencyKeyForUnsafeMethods(),
                      body.budgetPercent(),
                      body.budgetMinRetriesPerSecond(),
                      body.budgetWindowSeconds(),
                      body.enabled()))
          .flatMap(entity -> ServerResponse.ok().bodyValue(RetryPolicyResponse.from(entity)))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> bindPolicy(ServerRequest request) {
      UUID routeId = uuid(request, "routeId");
      return request
          .bodyToMono(BindRetryPolicyRequest.class)
          .flatMap(body -> retryRouteBindingService.bindRetryPolicy(routeId, body.retryPolicyId()))
          .flatMap(entity -> ServerResponse.ok().bodyValue(RouteRetryBindingResponse.from(entity)))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> unbindPolicy(ServerRequest request) {
      UUID routeId = uuid(request, "routeId");
      return retryRouteBindingService
          .clearRetryPolicy(routeId)
          .flatMap(entity -> ServerResponse.ok().bodyValue(RouteRetryBindingResponse.from(entity)))
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

  record CreateRetryPolicyRequest(
      String name,
      int maxAttempts,
      int perAttemptTimeoutMs,
      boolean retryOnConnectFailure,
      boolean retryOnConnectionReset,
      boolean retryOnDnsFailure,
      boolean retryOnResponseTimeout,
      String[] retryableMethods,
      boolean requireIdempotencyKeyForUnsafeMethods,
      int budgetPercent,
      int budgetMinRetriesPerSecond,
      int budgetWindowSeconds,
      boolean enabled) {}

  record PatchRetryPolicyRequest(
      Integer maxAttempts,
      Integer perAttemptTimeoutMs,
      Boolean retryOnConnectFailure,
      Boolean retryOnConnectionReset,
      Boolean retryOnDnsFailure,
      Boolean retryOnResponseTimeout,
      String[] retryableMethods,
      Boolean requireIdempotencyKeyForUnsafeMethods,
      Integer budgetPercent,
      Integer budgetMinRetriesPerSecond,
      Integer budgetWindowSeconds,
      Boolean enabled) {}

  record BindRetryPolicyRequest(UUID retryPolicyId) {}

  record RetryPolicyResponse(
      UUID id,
      UUID apiId,
      String name,
      int maxAttempts,
      int perAttemptTimeoutMs,
      boolean retryOnConnectFailure,
      boolean retryOnConnectionReset,
      boolean retryOnDnsFailure,
      boolean retryOnResponseTimeout,
      String[] retryableMethods,
      boolean requireIdempotencyKeyForUnsafeMethods,
      int budgetPercent,
      int budgetMinRetriesPerSecond,
      int budgetWindowSeconds,
      boolean enabled,
      String createdAt,
      String updatedAt) {

    static RetryPolicyResponse from(RetryPolicyEntity entity) {
      return new RetryPolicyResponse(
          entity.id(),
          entity.apiId(),
          entity.name(),
          entity.maxAttempts(),
          entity.perAttemptTimeoutMs(),
          entity.retryOnConnectFailure(),
          entity.retryOnConnectionReset(),
          entity.retryOnDnsFailure(),
          entity.retryOnResponseTimeout(),
          entity.retryableMethods(),
          entity.requireIdempotencyKeyForUnsafeMethods(),
          entity.budgetPercent(),
          entity.budgetMinRetriesPerSecond(),
          entity.budgetWindowSeconds(),
          entity.enabled(),
          entity.createdAt().toString(),
          entity.updatedAt().toString());
    }
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  record RouteRetryBindingResponse(UUID routeId, UUID retryPolicyId) {

    static RouteRetryBindingResponse from(RoutePolicyBindingEntity entity) {
      return new RouteRetryBindingResponse(entity.routeId(), entity.retryPolicyId());
    }
  }
}
