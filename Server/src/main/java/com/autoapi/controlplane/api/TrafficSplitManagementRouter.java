package com.autoapi.controlplane.api;

import com.autoapi.controlplane.persistence.RoutePolicyBindingEntity;
import com.autoapi.controlplane.persistence.TrafficSplitDestinationEntity;
import com.autoapi.controlplane.persistence.TrafficSplitPolicyEntity;
import com.autoapi.controlplane.traffic.TrafficSplitDestinationService;
import com.autoapi.controlplane.traffic.TrafficSplitPolicyService;
import com.autoapi.controlplane.traffic.TrafficSplitRouteBindingService;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
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
public class TrafficSplitManagementRouter {

  @Bean
  @Order(10)
  RouterFunction<ServerResponse> trafficSplitRoutes(
      TrafficSplitPolicyService policyService,
      TrafficSplitDestinationService destinationService,
      TrafficSplitRouteBindingService routeBindingService) {
    Handler handler = new Handler(policyService, destinationService, routeBindingService);
    return RouterFunctions.route()
        .path(
            "/api/v1",
            builder ->
                builder
                    .POST("/apis/{apiId}/traffic-split-policies", handler::createPolicy)
                    .GET("/apis/{apiId}/traffic-split-policies", handler::listPolicies)
                    .GET("/apis/{apiId}/traffic-split-policies/{policyId}", handler::getPolicy)
                    .PATCH("/apis/{apiId}/traffic-split-policies/{policyId}", handler::patchPolicy)
                    .POST(
                        "/traffic-split-policies/{policyId}/destinations", handler::addDestination)
                    .PATCH(
                        "/traffic-split-policies/{policyId}/destinations/{destinationId}",
                        handler::patchDestination)
                    .DELETE(
                        "/traffic-split-policies/{policyId}/destinations/{destinationId}",
                        handler::deleteDestination)
                    .PUT("/routes/{routeId}/traffic-split-policy", handler::bindPolicy)
                    .DELETE("/routes/{routeId}/traffic-split-policy", handler::unbindPolicy))
        .build();
  }

  static final class Handler {

    private final TrafficSplitPolicyService policyService;
    private final TrafficSplitDestinationService destinationService;
    private final TrafficSplitRouteBindingService routeBindingService;

    Handler(
        TrafficSplitPolicyService policyService,
        TrafficSplitDestinationService destinationService,
        TrafficSplitRouteBindingService routeBindingService) {
      this.policyService = policyService;
      this.destinationService = destinationService;
      this.routeBindingService = routeBindingService;
    }

    Mono<ServerResponse> createPolicy(ServerRequest request) {
      UUID apiId = uuid(request, "apiId");
      return request
          .bodyToMono(CreateTrafficSplitPolicyRequest.class)
          .flatMap(
              body ->
                  policyService.create(
                      apiId,
                      body.name(),
                      body.selectionKey(),
                      body.selectionKeyName(),
                      body.fallbackMode(),
                      body.enabled()))
          .flatMap(
              entity ->
                  ServerResponse.status(HttpStatus.CREATED)
                      .bodyValue(TrafficSplitPolicyResponse.from(entity)))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> listPolicies(ServerRequest request) {
      UUID apiId = uuid(request, "apiId");
      return policyService
          .list(apiId)
          .map(TrafficSplitPolicyResponse::from)
          .collectList()
          .flatMap(list -> ServerResponse.ok().bodyValue(list))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> getPolicy(ServerRequest request) {
      UUID apiId = uuid(request, "apiId");
      UUID policyId = uuid(request, "policyId");
      return policyService
          .get(apiId, policyId)
          .flatMap(
              policy ->
                  policyService
                      .listDestinations(policyId)
                      .map(TrafficSplitDestinationResponse::from)
                      .collectList()
                      .map(
                          destinations ->
                              TrafficSplitPolicyDetailResponse.from(policy, destinations)))
          .flatMap(detail -> ServerResponse.ok().bodyValue(detail))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> patchPolicy(ServerRequest request) {
      UUID apiId = uuid(request, "apiId");
      UUID policyId = uuid(request, "policyId");
      return request
          .bodyToMono(PatchTrafficSplitPolicyRequest.class)
          .flatMap(
              body ->
                  policyService.patch(
                      apiId,
                      policyId,
                      body.name(),
                      body.selectionKey(),
                      body.selectionKeyName(),
                      body.fallbackMode(),
                      body.enabled()))
          .flatMap(entity -> ServerResponse.ok().bodyValue(TrafficSplitPolicyResponse.from(entity)))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> addDestination(ServerRequest request) {
      UUID policyId = uuid(request, "policyId");
      return request
          .bodyToMono(AddTrafficSplitDestinationRequest.class)
          .flatMap(
              body ->
                  destinationService.addDestination(
                      policyId,
                      body.name(),
                      body.upstreamPoolId(),
                      body.weight(),
                      body.priority(),
                      body.primary()))
          .flatMap(
              entity ->
                  ServerResponse.status(HttpStatus.CREATED)
                      .bodyValue(TrafficSplitDestinationResponse.from(entity)))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> patchDestination(ServerRequest request) {
      UUID policyId = uuid(request, "policyId");
      UUID destinationId = uuid(request, "destinationId");
      return request
          .bodyToMono(PatchTrafficSplitDestinationRequest.class)
          .flatMap(
              body ->
                  destinationService.patchDestination(
                      policyId,
                      destinationId,
                      body.name(),
                      body.weight(),
                      body.priority(),
                      body.primary()))
          .flatMap(
              entity -> ServerResponse.ok().bodyValue(TrafficSplitDestinationResponse.from(entity)))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> deleteDestination(ServerRequest request) {
      UUID policyId = uuid(request, "policyId");
      UUID destinationId = uuid(request, "destinationId");
      return destinationService
          .deleteDestination(policyId, destinationId)
          .then(ServerResponse.noContent().build())
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> bindPolicy(ServerRequest request) {
      UUID routeId = uuid(request, "routeId");
      return request
          .bodyToMono(BindTrafficSplitPolicyRequest.class)
          .flatMap(
              body ->
                  routeBindingService.bindTrafficSplitPolicy(routeId, body.trafficSplitPolicyId()))
          .flatMap(
              entity ->
                  ServerResponse.ok().bodyValue(RouteTrafficSplitBindingResponse.from(entity)))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> unbindPolicy(ServerRequest request) {
      UUID routeId = uuid(request, "routeId");
      return routeBindingService
          .clearTrafficSplitPolicy(routeId)
          .flatMap(
              entity ->
                  ServerResponse.ok().bodyValue(RouteTrafficSplitBindingResponse.from(entity)))
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

  record CreateTrafficSplitPolicyRequest(
      String name,
      String selectionKey,
      String selectionKeyName,
      String fallbackMode,
      boolean enabled) {}

  record PatchTrafficSplitPolicyRequest(
      String name,
      String selectionKey,
      String selectionKeyName,
      String fallbackMode,
      Boolean enabled) {}

  record AddTrafficSplitDestinationRequest(
      String name, UUID upstreamPoolId, int weight, int priority, boolean primary) {}

  record PatchTrafficSplitDestinationRequest(
      String name, Integer weight, Integer priority, Boolean primary) {}

  record BindTrafficSplitPolicyRequest(UUID trafficSplitPolicyId) {}

  record TrafficSplitPolicyResponse(
      UUID id,
      UUID apiId,
      String name,
      String selectionKey,
      String selectionKeyName,
      String fallbackMode,
      boolean enabled,
      String createdAt,
      String updatedAt) {

    static TrafficSplitPolicyResponse from(TrafficSplitPolicyEntity entity) {
      return new TrafficSplitPolicyResponse(
          entity.id(),
          entity.apiId(),
          entity.name(),
          entity.selectionKey(),
          entity.selectionKeyName(),
          entity.fallbackMode(),
          entity.enabled(),
          entity.createdAt().toString(),
          entity.updatedAt().toString());
    }
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  record TrafficSplitPolicyDetailResponse(
      UUID id,
      UUID apiId,
      String name,
      String selectionKey,
      String selectionKeyName,
      String fallbackMode,
      boolean enabled,
      String createdAt,
      String updatedAt,
      List<TrafficSplitDestinationResponse> destinations) {

    static TrafficSplitPolicyDetailResponse from(
        TrafficSplitPolicyEntity entity, List<TrafficSplitDestinationResponse> destinations) {
      return new TrafficSplitPolicyDetailResponse(
          entity.id(),
          entity.apiId(),
          entity.name(),
          entity.selectionKey(),
          entity.selectionKeyName(),
          entity.fallbackMode(),
          entity.enabled(),
          entity.createdAt().toString(),
          entity.updatedAt().toString(),
          destinations);
    }
  }

  record TrafficSplitDestinationResponse(
      UUID id,
      UUID trafficSplitPolicyId,
      UUID upstreamPoolId,
      String name,
      int weight,
      int priority,
      boolean primary,
      String createdAt,
      String updatedAt) {

    static TrafficSplitDestinationResponse from(TrafficSplitDestinationEntity entity) {
      return new TrafficSplitDestinationResponse(
          entity.id(),
          entity.trafficSplitPolicyId(),
          entity.upstreamPoolId(),
          entity.name(),
          entity.weight(),
          entity.priority(),
          entity.primary(),
          entity.createdAt().toString(),
          entity.updatedAt().toString());
    }
  }

  record RouteTrafficSplitBindingResponse(UUID routeId, UUID trafficSplitPolicyId) {

    static RouteTrafficSplitBindingResponse from(RoutePolicyBindingEntity entity) {
      return new RouteTrafficSplitBindingResponse(entity.routeId(), entity.trafficSplitPolicyId());
    }
  }
}
