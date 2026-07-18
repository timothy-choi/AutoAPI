package com.autoapi.controlplane.api;

import com.autoapi.controlplane.circuitbreaker.CircuitBreakerPolicyService;
import com.autoapi.controlplane.circuitbreaker.CircuitBreakerRouteBindingService;
import com.autoapi.controlplane.persistence.CircuitBreakerPolicyEntity;
import com.autoapi.controlplane.persistence.RoutePolicyBindingEntity;
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
public class CircuitBreakerManagementRouter {

  @Bean
  @Order(11)
  RouterFunction<ServerResponse> circuitBreakerRoutes(
      CircuitBreakerPolicyService circuitBreakerPolicyService,
      CircuitBreakerRouteBindingService circuitBreakerRouteBindingService) {
    Handler handler = new Handler(circuitBreakerPolicyService, circuitBreakerRouteBindingService);
    return RouterFunctions.route()
        .path(
            "/api/v1",
            builder ->
                builder
                    .POST("/apis/{apiId}/circuit-breaker-policies", handler::createPolicy)
                    .GET("/apis/{apiId}/circuit-breaker-policies", handler::listPolicies)
                    .GET("/apis/{apiId}/circuit-breaker-policies/{policyId}", handler::getPolicy)
                    .PATCH(
                        "/apis/{apiId}/circuit-breaker-policies/{policyId}", handler::patchPolicy)
                    .DELETE(
                        "/apis/{apiId}/circuit-breaker-policies/{policyId}", handler::deletePolicy)
                    .PUT("/routes/{routeId}/circuit-breaker-policy", handler::bindPolicy)
                    .DELETE("/routes/{routeId}/circuit-breaker-policy", handler::unbindPolicy))
        .build();
  }

  static final class Handler {

    private final CircuitBreakerPolicyService circuitBreakerPolicyService;
    private final CircuitBreakerRouteBindingService circuitBreakerRouteBindingService;

    Handler(
        CircuitBreakerPolicyService circuitBreakerPolicyService,
        CircuitBreakerRouteBindingService circuitBreakerRouteBindingService) {
      this.circuitBreakerPolicyService = circuitBreakerPolicyService;
      this.circuitBreakerRouteBindingService = circuitBreakerRouteBindingService;
    }

    Mono<ServerResponse> createPolicy(ServerRequest request) {
      UUID apiId = uuid(request, "apiId");
      return request
          .bodyToMono(CreateCircuitBreakerPolicyRequest.class)
          .flatMap(
              body ->
                  circuitBreakerPolicyService.create(
                      apiId,
                      body.name(),
                      body.failureThreshold(),
                      body.rollingWindowSeconds(),
                      body.openDurationSeconds(),
                      body.halfOpenMaxRequests(),
                      body.successThreshold(),
                      body.failurePredicate().countHttp5xx(),
                      body.failurePredicate().countConnectFailure(),
                      body.failurePredicate().countConnectTimeout(),
                      body.failurePredicate().countReadTimeout(),
                      body.failurePredicate().countTlsFailure(),
                      body.failurePredicate().countTransportException(),
                      body.failurePredicate().countHttp429(),
                      body.enabled()))
          .flatMap(
              entity ->
                  ServerResponse.status(HttpStatus.CREATED)
                      .bodyValue(CircuitBreakerPolicyResponse.from(entity)))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> listPolicies(ServerRequest request) {
      UUID apiId = uuid(request, "apiId");
      return circuitBreakerPolicyService
          .list(apiId)
          .map(CircuitBreakerPolicyResponse::from)
          .collectList()
          .flatMap(list -> ServerResponse.ok().bodyValue(list))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> getPolicy(ServerRequest request) {
      UUID apiId = uuid(request, "apiId");
      UUID policyId = uuid(request, "policyId");
      return circuitBreakerPolicyService
          .get(apiId, policyId)
          .flatMap(
              entity -> ServerResponse.ok().bodyValue(CircuitBreakerPolicyResponse.from(entity)))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> patchPolicy(ServerRequest request) {
      UUID apiId = uuid(request, "apiId");
      UUID policyId = uuid(request, "policyId");
      return request
          .bodyToMono(PatchCircuitBreakerPolicyRequest.class)
          .flatMap(
              body ->
                  circuitBreakerPolicyService.patch(
                      apiId,
                      policyId,
                      body.failureThreshold(),
                      body.rollingWindowSeconds(),
                      body.openDurationSeconds(),
                      body.halfOpenMaxRequests(),
                      body.successThreshold(),
                      body.failurePredicate() == null
                          ? null
                          : body.failurePredicate().countHttp5xx(),
                      body.failurePredicate() == null
                          ? null
                          : body.failurePredicate().countConnectFailure(),
                      body.failurePredicate() == null
                          ? null
                          : body.failurePredicate().countConnectTimeout(),
                      body.failurePredicate() == null
                          ? null
                          : body.failurePredicate().countReadTimeout(),
                      body.failurePredicate() == null
                          ? null
                          : body.failurePredicate().countTlsFailure(),
                      body.failurePredicate() == null
                          ? null
                          : body.failurePredicate().countTransportException(),
                      body.failurePredicate() == null
                          ? null
                          : body.failurePredicate().countHttp429(),
                      body.enabled()))
          .flatMap(
              entity -> ServerResponse.ok().bodyValue(CircuitBreakerPolicyResponse.from(entity)))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> deletePolicy(ServerRequest request) {
      UUID apiId = uuid(request, "apiId");
      UUID policyId = uuid(request, "policyId");
      return circuitBreakerPolicyService
          .delete(apiId, policyId)
          .then(ServerResponse.noContent().build())
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> bindPolicy(ServerRequest request) {
      UUID routeId = uuid(request, "routeId");
      return request
          .bodyToMono(BindCircuitBreakerPolicyRequest.class)
          .flatMap(
              body ->
                  circuitBreakerRouteBindingService.bindCircuitBreakerPolicy(
                      routeId, body.circuitBreakerPolicyId()))
          .flatMap(
              entity ->
                  ServerResponse.ok().bodyValue(RouteCircuitBreakerBindingResponse.from(entity)))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> unbindPolicy(ServerRequest request) {
      UUID routeId = uuid(request, "routeId");
      return circuitBreakerRouteBindingService
          .clearCircuitBreakerPolicy(routeId)
          .flatMap(
              entity ->
                  ServerResponse.ok().bodyValue(RouteCircuitBreakerBindingResponse.from(entity)))
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

  record FailurePredicateRequest(
      boolean countHttp5xx,
      boolean countConnectFailure,
      boolean countConnectTimeout,
      boolean countReadTimeout,
      boolean countTlsFailure,
      boolean countTransportException,
      boolean countHttp429) {}

  record CreateCircuitBreakerPolicyRequest(
      String name,
      int failureThreshold,
      int rollingWindowSeconds,
      int openDurationSeconds,
      int halfOpenMaxRequests,
      int successThreshold,
      FailurePredicateRequest failurePredicate,
      boolean enabled) {}

  record PatchCircuitBreakerPolicyRequest(
      Integer failureThreshold,
      Integer rollingWindowSeconds,
      Integer openDurationSeconds,
      Integer halfOpenMaxRequests,
      Integer successThreshold,
      FailurePredicateRequest failurePredicate,
      Boolean enabled) {}

  record BindCircuitBreakerPolicyRequest(UUID circuitBreakerPolicyId) {}

  record FailurePredicateResponse(
      boolean countHttp5xx,
      boolean countConnectFailure,
      boolean countConnectTimeout,
      boolean countReadTimeout,
      boolean countTlsFailure,
      boolean countTransportException,
      boolean countHttp429) {

    static FailurePredicateResponse from(CircuitBreakerPolicyEntity entity) {
      return new FailurePredicateResponse(
          entity.predicateCountHttp5xx(),
          entity.predicateCountConnectFailure(),
          entity.predicateCountConnectTimeout(),
          entity.predicateCountReadTimeout(),
          entity.predicateCountTlsFailure(),
          entity.predicateCountTransportException(),
          entity.predicateCountHttp429());
    }
  }

  record CircuitBreakerPolicyResponse(
      UUID id,
      UUID apiId,
      String name,
      int failureThreshold,
      int rollingWindowSeconds,
      int openDurationSeconds,
      int halfOpenMaxRequests,
      int successThreshold,
      FailurePredicateResponse failurePredicate,
      boolean enabled,
      String createdAt,
      String updatedAt) {

    static CircuitBreakerPolicyResponse from(CircuitBreakerPolicyEntity entity) {
      return new CircuitBreakerPolicyResponse(
          entity.id(),
          entity.apiId(),
          entity.name(),
          entity.failureThreshold(),
          entity.rollingWindowSeconds(),
          entity.openDurationSeconds(),
          entity.halfOpenMaxRequests(),
          entity.successThreshold(),
          FailurePredicateResponse.from(entity),
          entity.enabled(),
          entity.createdAt().toString(),
          entity.updatedAt().toString());
    }
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  record RouteCircuitBreakerBindingResponse(UUID routeId, UUID circuitBreakerPolicyId) {

    static RouteCircuitBreakerBindingResponse from(RoutePolicyBindingEntity entity) {
      return new RouteCircuitBreakerBindingResponse(
          entity.routeId(), entity.circuitBreakerPolicyId());
    }
  }
}
