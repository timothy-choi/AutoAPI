package com.autoapi.controlplane.api;

import com.autoapi.controlplane.apikey.ApiKeyService;
import com.autoapi.controlplane.apikey.ApiKeyService.CreatedApiKey;
import com.autoapi.controlplane.persistence.ApiKeyEntity;
import com.autoapi.controlplane.persistence.RateLimitPolicyEntity;
import com.autoapi.controlplane.persistence.RoutePolicyBindingEntity;
import com.autoapi.controlplane.ratelimit.RateLimitPolicyService;
import com.autoapi.controlplane.routepolicy.RoutePolicyBindingService;
import java.time.OffsetDateTime;
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
public class SecurityManagementRouter {

  @Bean
  @Order(7)
  RouterFunction<ServerResponse> securityRoutes(
      ApiKeyService apiKeyService,
      RateLimitPolicyService rateLimitPolicyService,
      RoutePolicyBindingService routePolicyBindingService) {
    Handler handler = new Handler(apiKeyService, rateLimitPolicyService, routePolicyBindingService);
    return RouterFunctions.route()
        .path(
            "/api/v1",
            builder ->
                builder
                    .POST("/apis/{apiId}/api-keys", handler::createApiKey)
                    .GET("/apis/{apiId}/api-keys", handler::listApiKeys)
                    .GET("/apis/{apiId}/api-keys/{keyId}", handler::getApiKey)
                    .POST("/apis/{apiId}/api-keys/{keyId}/revoke", handler::revokeApiKey)
                    .POST("/apis/{apiId}/rate-limit-policies", handler::createPolicy)
                    .GET("/apis/{apiId}/rate-limit-policies", handler::listPolicies)
                    .GET("/apis/{apiId}/rate-limit-policies/{policyId}", handler::getPolicy)
                    .PATCH("/apis/{apiId}/rate-limit-policies/{policyId}", handler::patchPolicy)
                    .PUT("/routes/{routeId}/policy-binding", handler::upsertBinding)
                    .GET("/routes/{routeId}/policy-binding", handler::getBinding)
                    .DELETE("/routes/{routeId}/policy-binding", handler::deleteBinding))
        .build();
  }

  static final class Handler {

    private final ApiKeyService apiKeyService;
    private final RateLimitPolicyService rateLimitPolicyService;
    private final RoutePolicyBindingService routePolicyBindingService;

    Handler(
        ApiKeyService apiKeyService,
        RateLimitPolicyService rateLimitPolicyService,
        RoutePolicyBindingService routePolicyBindingService) {
      this.apiKeyService = apiKeyService;
      this.rateLimitPolicyService = rateLimitPolicyService;
      this.routePolicyBindingService = routePolicyBindingService;
    }

    Mono<ServerResponse> createApiKey(ServerRequest request) {
      UUID apiId = uuid(request, "apiId");
      return request
          .bodyToMono(CreateApiKeyRequest.class)
          .flatMap(body -> apiKeyService.create(apiId, body.name(), body.expiresAt()))
          .flatMap(
              result ->
                  ServerResponse.status(HttpStatus.CREATED)
                      .bodyValue(ApiKeyCreatedResponse.from(result)))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> listApiKeys(ServerRequest request) {
      UUID apiId = uuid(request, "apiId");
      return apiKeyService
          .list(apiId)
          .map(ApiKeyMetadataResponse::from)
          .collectList()
          .flatMap(list -> ServerResponse.ok().bodyValue(list))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> getApiKey(ServerRequest request) {
      UUID apiId = uuid(request, "apiId");
      String keyId = request.pathVariable("keyId");
      return apiKeyService
          .get(apiId, keyId)
          .flatMap(entity -> ServerResponse.ok().bodyValue(ApiKeyMetadataResponse.from(entity)))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> revokeApiKey(ServerRequest request) {
      UUID apiId = uuid(request, "apiId");
      String keyId = request.pathVariable("keyId");
      return apiKeyService
          .revoke(apiId, keyId)
          .flatMap(entity -> ServerResponse.ok().bodyValue(ApiKeyMetadataResponse.from(entity)))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> createPolicy(ServerRequest request) {
      UUID apiId = uuid(request, "apiId");
      return request
          .bodyToMono(CreateRateLimitPolicyRequest.class)
          .flatMap(
              body ->
                  rateLimitPolicyService.create(
                      apiId,
                      body.name(),
                      body.limitCount(),
                      body.windowSeconds(),
                      body.identitySource(),
                      body.redisFailureMode()))
          .flatMap(
              entity ->
                  ServerResponse.status(HttpStatus.CREATED)
                      .bodyValue(RateLimitPolicyResponse.from(entity)))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> listPolicies(ServerRequest request) {
      UUID apiId = uuid(request, "apiId");
      return rateLimitPolicyService
          .list(apiId)
          .map(RateLimitPolicyResponse::from)
          .collectList()
          .flatMap(list -> ServerResponse.ok().bodyValue(list))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> getPolicy(ServerRequest request) {
      UUID apiId = uuid(request, "apiId");
      UUID policyId = uuid(request, "policyId");
      return rateLimitPolicyService
          .get(apiId, policyId)
          .flatMap(entity -> ServerResponse.ok().bodyValue(RateLimitPolicyResponse.from(entity)))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> patchPolicy(ServerRequest request) {
      UUID apiId = uuid(request, "apiId");
      UUID policyId = uuid(request, "policyId");
      return request
          .bodyToMono(PatchRateLimitPolicyRequest.class)
          .flatMap(
              body ->
                  rateLimitPolicyService.patch(
                      apiId,
                      policyId,
                      body.limitCount(),
                      body.windowSeconds(),
                      body.identitySource(),
                      body.redisFailureMode(),
                      body.enabled()))
          .flatMap(entity -> ServerResponse.ok().bodyValue(RateLimitPolicyResponse.from(entity)))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> upsertBinding(ServerRequest request) {
      UUID routeId = uuid(request, "routeId");
      return request
          .bodyToMono(PolicyBindingRequest.class)
          .flatMap(
              body ->
                  routePolicyBindingService.upsert(
                      routeId, body.authenticationRequired(), body.rateLimitPolicyId()))
          .flatMap(entity -> ServerResponse.ok().bodyValue(PolicyBindingResponse.from(entity)))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> getBinding(ServerRequest request) {
      UUID routeId = uuid(request, "routeId");
      return routePolicyBindingService
          .get(routeId)
          .flatMap(entity -> ServerResponse.ok().bodyValue(PolicyBindingResponse.from(entity)))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> deleteBinding(ServerRequest request) {
      UUID routeId = uuid(request, "routeId");
      return routePolicyBindingService
          .delete(routeId)
          .then(ServerResponse.noContent().build())
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

  record CreateApiKeyRequest(String name, OffsetDateTime expiresAt) {}

  record ApiKeyCreatedResponse(
      UUID id,
      UUID apiId,
      String keyId,
      String name,
      String keyPrefix,
      String plaintextKey,
      String expiresAt,
      String createdAt) {

    static ApiKeyCreatedResponse from(CreatedApiKey created) {
      return new ApiKeyCreatedResponse(
          created.id(),
          created.apiId(),
          created.keyId(),
          created.name(),
          created.keyPrefix(),
          created.plaintextKey(),
          created.expiresAt() == null ? null : created.expiresAt().toString(),
          created.createdAt().toString());
    }
  }

  record ApiKeyMetadataResponse(
      UUID id,
      UUID apiId,
      String keyId,
      String name,
      String keyPrefix,
      boolean enabled,
      String expiresAt,
      String revokedAt,
      String createdAt) {

    static ApiKeyMetadataResponse from(ApiKeyEntity entity) {
      return new ApiKeyMetadataResponse(
          entity.id(),
          entity.apiId(),
          entity.keyId(),
          entity.name(),
          entity.keyPrefix(),
          entity.enabled(),
          entity.expiresAt() == null ? null : entity.expiresAt().toString(),
          entity.revokedAt() == null ? null : entity.revokedAt().toString(),
          entity.createdAt().toString());
    }
  }

  record CreateRateLimitPolicyRequest(
      String name,
      int limitCount,
      int windowSeconds,
      String identitySource,
      String redisFailureMode) {}

  record PatchRateLimitPolicyRequest(
      Integer limitCount,
      Integer windowSeconds,
      String identitySource,
      String redisFailureMode,
      Boolean enabled) {}

  record RateLimitPolicyResponse(
      UUID id,
      UUID apiId,
      String name,
      int limitCount,
      int windowSeconds,
      String identitySource,
      String redisFailureMode,
      boolean enabled,
      String createdAt,
      String updatedAt) {

    static RateLimitPolicyResponse from(RateLimitPolicyEntity entity) {
      return new RateLimitPolicyResponse(
          entity.id(),
          entity.apiId(),
          entity.name(),
          entity.limitCount(),
          entity.windowSeconds(),
          entity.identitySource(),
          entity.redisFailureMode(),
          entity.enabled(),
          entity.createdAt().toString(),
          entity.updatedAt().toString());
    }
  }

  record PolicyBindingRequest(boolean authenticationRequired, UUID rateLimitPolicyId) {}

  record PolicyBindingResponse(
      UUID routeId, boolean authenticationRequired, UUID rateLimitPolicyId, String updatedAt) {

    static PolicyBindingResponse from(RoutePolicyBindingEntity entity) {
      return new PolicyBindingResponse(
          entity.routeId(),
          entity.authenticationRequired(),
          entity.rateLimitPolicyId(),
          entity.updatedAt().toString());
    }
  }
}
