package com.autoapi.controlplane.policy;

import com.autoapi.controlplane.api.ControlPlaneException;
import com.autoapi.controlplane.events.EventContext;
import com.autoapi.controlplane.policy.bundle.PolicyBundleAssignmentService;
import com.autoapi.controlplane.policy.bundle.PolicyBundleService;
import com.autoapi.controlplane.policy.bundle.PolicyOverrideService;
import com.fasterxml.jackson.databind.JsonNode;
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
    name = "autoapi.controlplane.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class PolicyEngineRouter {

  @Bean
  @Order(16)
  RouterFunction<ServerResponse> policyEngineRoutes(
      PolicyBundleService bundleService,
      PolicyBundleAssignmentService assignmentService,
      PolicyOverrideService overrideService,
      EffectivePolicyService effectivePolicyService,
      PolicyEvaluationService evaluationService) {
    Handler handler =
        new Handler(
            bundleService,
            assignmentService,
            overrideService,
            effectivePolicyService,
            evaluationService);
    return RouterFunctions.route()
        .path(
            "/api/v1/management/organizations/{orgId}/policy-bundles",
            builder ->
                builder
                    .POST("", handler::createBundle)
                    .GET("", handler::listBundles)
                    .GET("/{bundleId}", handler::getBundle)
                    .PATCH("/{bundleId}", handler::updateBundle)
                    .POST("/{bundleId}/revisions", handler::createRevision)
                    .GET("/{bundleId}/revisions", handler::listRevisions)
                    .POST("/{bundleId}/assignments", handler::assignOrganization)
                    .DELETE("/{bundleId}/assignments", handler::detachOrganization))
        .path(
            "/api/v1/management/projects/{projectId}/policy-bundles/{bundleId}/assignments",
            builder -> builder.POST("", handler::assignProject).DELETE("", handler::detachProject))
        .path(
            "/api/v1/management/projects/{projectId}/gateway-groups/{groupId}/policy-bundles/{bundleId}/assignments",
            builder ->
                builder
                    .POST("", handler::assignGatewayGroup)
                    .DELETE("", handler::detachGatewayGroup))
        .path(
            "/api/v1/management/apis/{apiId}/policy-bundles/{bundleId}/assignments",
            builder -> builder.POST("", handler::assignApi).DELETE("", handler::detachApi))
        .path(
            "/api/v1/management/routes/{routeId}/policy-bundles/{bundleId}/assignments",
            builder -> builder.POST("", handler::assignRoute).DELETE("", handler::detachRoute))
        .path(
            "/api/v1/management/organizations/{orgId}/policy-overrides",
            builder ->
                builder.POST("", handler::createOrgOverride).GET("", handler::listOrgOverrides))
        .path(
            "/api/v1/management/projects/{projectId}/policy-overrides",
            builder ->
                builder
                    .POST("", handler::createProjectOverride)
                    .GET("", handler::listProjectOverrides))
        .path(
            "/api/v1/management/projects/{projectId}/gateway-groups/{groupId}/policy-overrides",
            builder ->
                builder
                    .POST("", handler::createGatewayGroupOverride)
                    .GET("", handler::listGatewayGroupOverrides))
        .path(
            "/api/v1/management/apis/{apiId}/policy-overrides",
            builder ->
                builder.POST("", handler::createApiOverride).GET("", handler::listApiOverrides))
        .path(
            "/api/v1/management/routes/{routeId}/policy-overrides",
            builder ->
                builder.POST("", handler::createRouteOverride).GET("", handler::listRouteOverrides))
        .GET("/api/v1/management/policy-overrides/{overrideId}", handler::getOverride)
        .PATCH("/api/v1/management/policy-overrides/{overrideId}", handler::updateOverride)
        .DELETE("/api/v1/management/policy-overrides/{overrideId}", handler::deleteOverride)
        .GET("/api/v1/management/apis/{apiId}/effective-policy", handler::effectivePolicy)
        .POST("/api/v1/management/policies/evaluate", handler::evaluatePolicy)
        .build();
  }

  static final class Handler {

    private final PolicyBundleService bundleService;
    private final PolicyBundleAssignmentService assignmentService;
    private final PolicyOverrideService overrideService;
    private final EffectivePolicyService effectivePolicyService;
    private final PolicyEvaluationService evaluationService;

    Handler(
        PolicyBundleService bundleService,
        PolicyBundleAssignmentService assignmentService,
        PolicyOverrideService overrideService,
        EffectivePolicyService effectivePolicyService,
        PolicyEvaluationService evaluationService) {
      this.bundleService = bundleService;
      this.assignmentService = assignmentService;
      this.overrideService = overrideService;
      this.effectivePolicyService = effectivePolicyService;
      this.evaluationService = evaluationService;
    }

    Mono<ServerResponse> createBundle(ServerRequest request) {
      UUID orgId = uuid(request, "orgId");
      return request
          .bodyToMono(CreatePolicyBundleRequest.class)
          .flatMap(
              body ->
                  bundleService
                      .create(
                          orgId, body.name(), body.description(), body.enabled(), context(request))
                      .flatMap(view -> ServerResponse.ok().bodyValue(view)))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> listBundles(ServerRequest request) {
      int limit = request.queryParam("limit").map(Integer::parseInt).orElse(50);
      int offset = request.queryParam("offset").map(Integer::parseInt).orElse(0);
      return bundleService
          .list(uuid(request, "orgId"), limit, offset)
          .collectList()
          .flatMap(list -> ServerResponse.ok().bodyValue(list))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> getBundle(ServerRequest request) {
      return bundleService
          .get(uuid(request, "orgId"), uuid(request, "bundleId"))
          .flatMap(view -> ServerResponse.ok().bodyValue(view))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> updateBundle(ServerRequest request) {
      UUID orgId = uuid(request, "orgId");
      UUID bundleId = uuid(request, "bundleId");
      return request
          .bodyToMono(UpdatePolicyBundleRequest.class)
          .flatMap(
              body ->
                  bundleService
                      .update(orgId, bundleId, body.description(), body.enabled(), context(request))
                      .flatMap(view -> ServerResponse.ok().bodyValue(view)))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> createRevision(ServerRequest request) {
      UUID orgId = uuid(request, "orgId");
      UUID bundleId = uuid(request, "bundleId");
      return request
          .bodyToMono(CreatePolicyBundleRevisionRequest.class)
          .flatMap(
              body ->
                  bundleService
                      .createRevision(
                          orgId, bundleId, body.content(), body.message(), context(request))
                      .flatMap(view -> ServerResponse.ok().bodyValue(view)))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> listRevisions(ServerRequest request) {
      int limit = request.queryParam("limit").map(Integer::parseInt).orElse(50);
      int offset = request.queryParam("offset").map(Integer::parseInt).orElse(0);
      return bundleService
          .listRevisions(uuid(request, "orgId"), uuid(request, "bundleId"), limit, offset)
          .collectList()
          .flatMap(list -> ServerResponse.ok().bodyValue(list))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> assignOrganization(ServerRequest request) {
      return assignRequest(request)
          .flatMap(
              body ->
                  assignmentService.assignAtOrganization(
                      uuid(request, "orgId"),
                      uuid(request, "bundleId"),
                      body.revisionNumber(),
                      context(request)))
          .flatMap(view -> ServerResponse.ok().bodyValue(view))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> detachOrganization(ServerRequest request) {
      return assignmentService
          .detachAtOrganization(uuid(request, "orgId"), uuid(request, "bundleId"), context(request))
          .then(ServerResponse.noContent().build())
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> assignProject(ServerRequest request) {
      return assignRequest(request)
          .flatMap(
              body ->
                  assignmentService.assignAtProject(
                      uuid(request, "projectId"),
                      uuid(request, "bundleId"),
                      body.revisionNumber(),
                      context(request)))
          .flatMap(view -> ServerResponse.ok().bodyValue(view))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> detachProject(ServerRequest request) {
      return assignmentService
          .detachAtProject(uuid(request, "projectId"), uuid(request, "bundleId"), context(request))
          .then(ServerResponse.noContent().build())
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> assignGatewayGroup(ServerRequest request) {
      return assignRequest(request)
          .flatMap(
              body ->
                  assignmentService.assignAtGatewayGroup(
                      uuid(request, "projectId"),
                      uuid(request, "groupId"),
                      uuid(request, "bundleId"),
                      body.revisionNumber(),
                      context(request)))
          .flatMap(view -> ServerResponse.ok().bodyValue(view))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> detachGatewayGroup(ServerRequest request) {
      return assignmentService
          .detachAtGatewayGroup(
              uuid(request, "projectId"),
              uuid(request, "groupId"),
              uuid(request, "bundleId"),
              context(request))
          .then(ServerResponse.noContent().build())
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> assignApi(ServerRequest request) {
      return assignRequest(request)
          .flatMap(
              body ->
                  assignmentService.assignAtApi(
                      uuid(request, "apiId"),
                      uuid(request, "bundleId"),
                      body.revisionNumber(),
                      context(request)))
          .flatMap(view -> ServerResponse.ok().bodyValue(view))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> detachApi(ServerRequest request) {
      return assignmentService
          .detachAtApi(uuid(request, "apiId"), uuid(request, "bundleId"), context(request))
          .then(ServerResponse.noContent().build())
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> assignRoute(ServerRequest request) {
      return assignRequest(request)
          .flatMap(
              body ->
                  assignmentService.assignAtRoute(
                      uuid(request, "routeId"),
                      uuid(request, "bundleId"),
                      body.revisionNumber(),
                      context(request)))
          .flatMap(view -> ServerResponse.ok().bodyValue(view))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> detachRoute(ServerRequest request) {
      return assignmentService
          .detachAtRoute(uuid(request, "routeId"), uuid(request, "bundleId"), context(request))
          .then(ServerResponse.noContent().build())
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> createOrgOverride(ServerRequest request) {
      return createOverride(
          request, "ORGANIZATION", uuid(request, "orgId"), null, null, null, null);
    }

    Mono<ServerResponse> listOrgOverrides(ServerRequest request) {
      return listOverrides("ORGANIZATION", uuid(request, "orgId"));
    }

    Mono<ServerResponse> createProjectOverride(ServerRequest request) {
      return createOverride(request, "PROJECT", null, uuid(request, "projectId"), null, null, null);
    }

    Mono<ServerResponse> listProjectOverrides(ServerRequest request) {
      return listOverrides("PROJECT", uuid(request, "projectId"));
    }

    Mono<ServerResponse> createGatewayGroupOverride(ServerRequest request) {
      return createOverride(
          request,
          "GATEWAY_GROUP",
          null,
          uuid(request, "projectId"),
          uuid(request, "groupId"),
          null,
          null);
    }

    Mono<ServerResponse> listGatewayGroupOverrides(ServerRequest request) {
      return listOverrides("GATEWAY_GROUP", uuid(request, "groupId"));
    }

    Mono<ServerResponse> createApiOverride(ServerRequest request) {
      return createOverride(request, "API", null, null, null, uuid(request, "apiId"), null);
    }

    Mono<ServerResponse> listApiOverrides(ServerRequest request) {
      return listOverrides("API", uuid(request, "apiId"));
    }

    Mono<ServerResponse> createRouteOverride(ServerRequest request) {
      return createOverride(request, "ROUTE", null, null, null, null, uuid(request, "routeId"));
    }

    Mono<ServerResponse> listRouteOverrides(ServerRequest request) {
      return listOverrides("ROUTE", uuid(request, "routeId"));
    }

    Mono<ServerResponse> getOverride(ServerRequest request) {
      return overrideService
          .get(uuid(request, "overrideId"))
          .flatMap(view -> ServerResponse.ok().bodyValue(view))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> updateOverride(ServerRequest request) {
      return request
          .bodyToMono(UpdatePolicyOverrideRequest.class)
          .flatMap(
              body ->
                  overrideService
                      .update(
                          uuid(request, "overrideId"),
                          body.mode(),
                          body.content(),
                          context(request))
                      .flatMap(view -> ServerResponse.ok().bodyValue(view)))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> deleteOverride(ServerRequest request) {
      return overrideService
          .delete(uuid(request, "overrideId"), context(request))
          .then(ServerResponse.noContent().build())
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> effectivePolicy(ServerRequest request) {
      UUID apiId = uuid(request, "apiId");
      boolean explain = request.queryParam("explain").map(Boolean::parseBoolean).orElse(false);
      boolean allRoutes = request.queryParam("allRoutes").map(Boolean::parseBoolean).orElse(false);
      if (allRoutes) {
        return effectivePolicyService
            .evaluateAllRoutes(apiId, explain)
            .flatMap(map -> ServerResponse.ok().bodyValue(map))
            .onErrorResume(ControlPlaneException.class, this::error);
      }
      return request
          .queryParam("routeId")
          .map(UUID::fromString)
          .map(
              routeId ->
                  effectivePolicyService
                      .evaluateRoute(apiId, routeId, explain)
                      .flatMap(doc -> ServerResponse.ok().bodyValue(doc)))
          .orElseGet(
              () ->
                  effectivePolicyService
                      .evaluateApi(apiId, explain)
                      .flatMap(doc -> ServerResponse.ok().bodyValue(doc)))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> evaluatePolicy(ServerRequest request) {
      return request
          .bodyToMono(PolicyEvaluationRequest.class)
          .flatMap(
              body ->
                  evaluationService
                      .evaluate(body, context(request))
                      .flatMap(result -> ServerResponse.ok().bodyValue(result.document())))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    private Mono<AssignPolicyBundleRequest> assignRequest(ServerRequest request) {
      return request
          .bodyToMono(AssignPolicyBundleRequest.class)
          .defaultIfEmpty(new AssignPolicyBundleRequest(null));
    }

    private Mono<ServerResponse> createOverride(
        ServerRequest request,
        String scopeLevel,
        UUID organizationId,
        UUID projectId,
        UUID gatewayGroupId,
        UUID apiId,
        UUID routeId) {
      return request
          .bodyToMono(CreatePolicyOverrideRequest.class)
          .flatMap(
              body ->
                  overrideService
                      .create(
                          scopeLevel,
                          organizationId,
                          projectId,
                          gatewayGroupId,
                          apiId,
                          routeId,
                          body.policyType(),
                          body.mode(),
                          body.content(),
                          context(request))
                      .flatMap(view -> ServerResponse.ok().bodyValue(view)))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    private Mono<ServerResponse> listOverrides(String scopeLevel, UUID scopeResourceId) {
      return overrideService
          .listByScope(scopeLevel, scopeResourceId)
          .collectList()
          .flatMap(list -> ServerResponse.ok().bodyValue(list))
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

  record CreatePolicyBundleRequest(String name, String description, Boolean enabled) {}

  record UpdatePolicyBundleRequest(String description, Boolean enabled) {}

  record CreatePolicyBundleRevisionRequest(JsonNode content, String message) {}

  record AssignPolicyBundleRequest(Integer revisionNumber) {}

  record CreatePolicyOverrideRequest(String policyType, String mode, JsonNode content) {}

  record UpdatePolicyOverrideRequest(String mode, JsonNode content) {}
}
