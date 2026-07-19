package com.autoapi.controlplane.rollout;

import com.autoapi.controlplane.api.ControlPlaneException;
import com.autoapi.controlplane.events.EventContext;
import java.util.Map;
import java.util.UUID;
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
    name = {"autoapi.controlplane.enabled", "autoapi.rollouts.enabled"},
    havingValue = "true",
    matchIfMissing = true)
public class GatewayGroupManagementRouter {

  @Bean
  @Order(15)
  RouterFunction<ServerResponse> gatewayGroupManagementRoutes(GatewayGroupService groupService) {
    Handler handler = new Handler(groupService);
    return RouterFunctions.route()
        .path(
            "/api/v1/management/projects/{projectId}/gateway-groups",
            builder ->
                builder
                    .POST("", handler::createGroup)
                    .GET("", handler::listGroups)
                    .GET("/{groupId}", handler::getGroup)
                    .PATCH("/{groupId}", handler::updateGroup)
                    .DELETE("/{groupId}", handler::deleteGroup)
                    .GET("/{groupId}/gateways", handler::listGroupGateways)
                    .POST("/{groupId}/gateways/{gatewayId}", handler::addGateway)
                    .DELETE("/{groupId}/gateways/{gatewayId}", handler::removeGateway)
                    .POST("/{groupId}/membership-preview", handler::previewMembership)
                    .GET("/{groupId}/convergence", handler::convergence))
        .build();
  }

  static final class Handler {

    private final GatewayGroupService groupService;

    Handler(GatewayGroupService groupService) {
      this.groupService = groupService;
    }

    Mono<ServerResponse> createGroup(ServerRequest request) {
      UUID projectId = uuid(request, "projectId");
      return request
          .bodyToMono(CreateGatewayGroupRequest.class)
          .flatMap(
              body ->
                  groupService
                      .create(
                          projectId,
                          body.apiId(),
                          body.name(),
                          body.description(),
                          body.region(),
                          body.zone(),
                          body.environment(),
                          body.selectorJson(),
                          body.enabled(),
                          context(request))
                      .flatMap(view -> ServerResponse.ok().bodyValue(view)))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> listGroups(ServerRequest request) {
      int limit = request.queryParam("limit").map(Integer::parseInt).orElse(50);
      int offset = request.queryParam("offset").map(Integer::parseInt).orElse(0);
      return groupService
          .list(uuid(request, "projectId"), limit, offset)
          .collectList()
          .flatMap(list -> ServerResponse.ok().bodyValue(list))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> getGroup(ServerRequest request) {
      return groupService
          .get(uuid(request, "projectId"), uuid(request, "groupId"))
          .flatMap(view -> ServerResponse.ok().bodyValue(view))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> updateGroup(ServerRequest request) {
      UUID projectId = uuid(request, "projectId");
      UUID groupId = uuid(request, "groupId");
      return request
          .bodyToMono(UpdateGatewayGroupRequest.class)
          .flatMap(
              body ->
                  groupService
                      .update(
                          projectId,
                          groupId,
                          body.description(),
                          body.region(),
                          body.zone(),
                          body.environment(),
                          body.selectorJson(),
                          body.enabled(),
                          body.desiredConfigVersion(),
                          body.expectedVersion(),
                          context(request))
                      .flatMap(view -> ServerResponse.ok().bodyValue(view)))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> deleteGroup(ServerRequest request) {
      return groupService
          .delete(uuid(request, "projectId"), uuid(request, "groupId"), context(request))
          .then(ServerResponse.noContent().build())
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> listGroupGateways(ServerRequest request) {
      return groupService
          .listGroupGateways(uuid(request, "projectId"), uuid(request, "groupId"))
          .collectList()
          .flatMap(list -> ServerResponse.ok().bodyValue(list))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> addGateway(ServerRequest request) {
      return groupService
          .addExplicitMembership(
              uuid(request, "projectId"),
              uuid(request, "groupId"),
              request.pathVariable("gatewayId"),
              context(request))
          .then(ServerResponse.noContent().build())
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> removeGateway(ServerRequest request) {
      return groupService
          .removeExplicitMembership(
              uuid(request, "projectId"),
              uuid(request, "groupId"),
              request.pathVariable("gatewayId"),
              context(request))
          .then(ServerResponse.noContent().build())
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> previewMembership(ServerRequest request) {
      return groupService
          .previewMembership(uuid(request, "projectId"), uuid(request, "groupId"))
          .flatMap(preview -> ServerResponse.ok().bodyValue(preview))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> convergence(ServerRequest request) {
      return groupService
          .convergenceSummary(uuid(request, "projectId"), uuid(request, "groupId"))
          .flatMap(summary -> ServerResponse.ok().bodyValue(summary))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    private static EventContext context(ServerRequest request) {
      return EventContext.managementApi(request.headers().firstHeader("X-Request-ID"));
    }

    private static UUID uuid(ServerRequest request, String name) {
      return UUID.fromString(request.pathVariable(name));
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

  record CreateGatewayGroupRequest(
      UUID apiId,
      String name,
      String description,
      String region,
      String zone,
      String environment,
      String selectorJson,
      Boolean enabled) {}

  record UpdateGatewayGroupRequest(
      String description,
      String region,
      String zone,
      String environment,
      String selectorJson,
      Boolean enabled,
      Long desiredConfigVersion,
      long expectedVersion) {}
}
