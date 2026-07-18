package com.autoapi.controlplane.api;

import com.autoapi.controlplane.activation.ConfigActivationService;
import com.autoapi.controlplane.apidefinition.ApiDefinitionService;
import com.autoapi.controlplane.configversion.ConfigVersionService;
import com.autoapi.controlplane.configversion.RuntimeContentHasher;
import com.autoapi.controlplane.configversion.StoredRuntimeSnapshot;
import com.autoapi.controlplane.persistence.ApiEntity;
import com.autoapi.controlplane.persistence.ConfigVersionEntity;
import com.autoapi.controlplane.persistence.ProjectEntity;
import com.autoapi.controlplane.persistence.RouteEntity;
import com.autoapi.controlplane.persistence.UpstreamPoolEntity;
import com.autoapi.controlplane.persistence.UpstreamTargetEntity;
import com.autoapi.controlplane.project.ProjectService;
import com.autoapi.controlplane.route.RouteService;
import com.autoapi.controlplane.upstream.UpstreamService;
import com.autoapi.controlplane.validation.ValidationResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class ControlPlaneRouter {

  @Bean
  @Order(5)
  RouterFunction<ServerResponse> controlPlaneRoutes(
      ProjectService projectService,
      ApiDefinitionService apiDefinitionService,
      UpstreamService upstreamService,
      RouteService routeService,
      ConfigVersionService configVersionService,
      ConfigActivationService configActivationService,
      ObjectMapper objectMapper) {
    ControlPlaneHandler handler =
        new ControlPlaneHandler(
            projectService,
            apiDefinitionService,
            upstreamService,
            routeService,
            configVersionService,
            configActivationService,
            objectMapper);
    return RouterFunctions.route()
        .path(
            "/api/v1",
            builder ->
                builder
                    .POST("/projects", handler::createProject)
                    .GET("/projects", handler::listProjects)
                    .GET("/projects/{projectId}", handler::getProject)
                    .POST("/projects/{projectId}/apis", handler::createApi)
                    .GET("/projects/{projectId}/apis", handler::listApis)
                    .GET("/apis/{apiId}", handler::getApi)
                    .POST("/apis/{apiId}/upstream-pools", handler::createPool)
                    .GET("/apis/{apiId}/upstream-pools", handler::listPools)
                    .POST("/upstream-pools/{poolId}/targets", handler::createTarget)
                    .GET("/upstream-pools/{poolId}/targets", handler::listTargets)
                    .POST("/apis/{apiId}/routes", handler::createRoute)
                    .GET("/apis/{apiId}/routes", handler::listRoutes)
                    .POST("/apis/{apiId}/config/validate", handler::validateConfig)
                    .POST("/apis/{apiId}/config/versions", handler::publishConfig)
                    .GET("/apis/{apiId}/config/versions", handler::listConfigVersions)
                    .GET("/apis/{apiId}/config/versions/{version}", handler::getConfigVersion)
                    .POST(
                        "/apis/{apiId}/config/versions/{version}/activate",
                        handler::activateConfig))
        .build();
  }

  static final class ControlPlaneHandler {

    private static final Logger log = LoggerFactory.getLogger(ControlPlaneHandler.class);

    private final ProjectService projectService;
    private final ApiDefinitionService apiDefinitionService;
    private final UpstreamService upstreamService;
    private final RouteService routeService;
    private final ConfigVersionService configVersionService;
    private final ConfigActivationService configActivationService;
    private final ObjectMapper objectMapper;

    ControlPlaneHandler(
        ProjectService projectService,
        ApiDefinitionService apiDefinitionService,
        UpstreamService upstreamService,
        RouteService routeService,
        ConfigVersionService configVersionService,
        ConfigActivationService configActivationService,
        ObjectMapper objectMapper) {
      this.projectService = projectService;
      this.apiDefinitionService = apiDefinitionService;
      this.upstreamService = upstreamService;
      this.routeService = routeService;
      this.configVersionService = configVersionService;
      this.configActivationService = configActivationService;
      this.objectMapper = objectMapper;
    }

    Mono<ServerResponse> createProject(ServerRequest request) {
      return request
          .bodyToMono(CreateProjectRequest.class)
          .flatMap(
              body ->
                  projectService
                      .create(
                          body.name(),
                          body.description(),
                          com.autoapi.controlplane.events.EventContext.managementApi(
                              request.headers().firstHeader("X-Request-ID")))
                      .flatMap(entity -> created(ProjectResponse.from(entity))))
          .onErrorResume(ControlPlaneException.class, this::error)
          .onErrorResume(ex -> unexpectedError(request, ex));
    }

    Mono<ServerResponse> listProjects(ServerRequest request) {
      return projectService
          .list()
          .map(ProjectResponse::from)
          .collectList()
          .flatMap(list -> ServerResponse.ok().bodyValue(list))
          .onErrorResume(ControlPlaneException.class, this::error)
          .onErrorResume(ex -> unexpectedError(request, ex));
    }

    Mono<ServerResponse> getProject(ServerRequest request) {
      return projectService
          .get(uuid(request, "projectId"))
          .flatMap(entity -> ServerResponse.ok().bodyValue(ProjectResponse.from(entity)))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> createApi(ServerRequest request) {
      return request
          .bodyToMono(CreateApiRequest.class)
          .flatMap(
              body ->
                  apiDefinitionService
                      .create(uuid(request, "projectId"), body.name(), body.host(), body.basePath())
                      .flatMap(entity -> created(ApiResponse.from(entity))))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> listApis(ServerRequest request) {
      return apiDefinitionService
          .listByProject(uuid(request, "projectId"))
          .map(ApiResponse::from)
          .collectList()
          .flatMap(list -> ServerResponse.ok().bodyValue(list))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> getApi(ServerRequest request) {
      return apiDefinitionService
          .get(uuid(request, "apiId"))
          .flatMap(entity -> ServerResponse.ok().bodyValue(ApiResponse.from(entity)))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> createPool(ServerRequest request) {
      return request
          .bodyToMono(CreatePoolRequest.class)
          .flatMap(
              body ->
                  upstreamService
                      .createPool(uuid(request, "apiId"), body.name(), body.loadBalancing())
                      .flatMap(entity -> created(UpstreamPoolResponse.from(entity))))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> listPools(ServerRequest request) {
      return upstreamService
          .listPools(uuid(request, "apiId"))
          .map(UpstreamPoolResponse::from)
          .collectList()
          .flatMap(list -> ServerResponse.ok().bodyValue(list))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> createTarget(ServerRequest request) {
      return request
          .bodyToMono(CreateTargetRequest.class)
          .flatMap(
              body ->
                  upstreamService
                      .createTarget(
                          uuid(request, "poolId"),
                          body.url(),
                          body.enabled() == null || body.enabled(),
                          body.weight() == null ? 1 : body.weight())
                      .flatMap(entity -> created(UpstreamTargetResponse.from(entity))))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> listTargets(ServerRequest request) {
      return upstreamService
          .listTargets(uuid(request, "poolId"))
          .map(UpstreamTargetResponse::from)
          .collectList()
          .flatMap(list -> ServerResponse.ok().bodyValue(list))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> createRoute(ServerRequest request) {
      return request
          .bodyToMono(CreateRouteRequest.class)
          .flatMap(
              body ->
                  routeService
                      .create(
                          uuid(request, "apiId"),
                          body.name(),
                          body.host(),
                          body.pathPrefix(),
                          body.methods(),
                          body.upstreamPoolId(),
                          body.enabled() == null || body.enabled())
                      .flatMap(entity -> created(RouteResponse.from(entity))))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> listRoutes(ServerRequest request) {
      return routeService
          .listByApi(uuid(request, "apiId"))
          .map(RouteResponse::from)
          .collectList()
          .flatMap(list -> ServerResponse.ok().bodyValue(list))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> validateConfig(ServerRequest request) {
      return configVersionService
          .validate(uuid(request, "apiId"))
          .flatMap(this::validationResponse)
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> publishConfig(ServerRequest request) {
      return request
          .bodyToMono(PublishConfigRequest.class)
          .flatMap(
              body ->
                  configVersionService
                      .publish(uuid(request, "apiId"), body.message())
                      .flatMap(entity -> created(ConfigVersionResponse.from(entity))))
          .onErrorResume(ControlPlaneException.class, this::error)
          .onErrorResume(ex -> unexpectedError(request, ex));
    }

    Mono<ServerResponse> listConfigVersions(ServerRequest request) {
      return configVersionService
          .listMetadata(uuid(request, "apiId"))
          .map(ConfigVersionResponse::from)
          .collectList()
          .flatMap(list -> ServerResponse.ok().bodyValue(list))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> activateConfig(ServerRequest request) {
      long version = Long.parseLong(request.pathVariable("version"));
      return request
          .bodyToMono(ActivateConfigRequest.class)
          .defaultIfEmpty(new ActivateConfigRequest(null))
          .flatMap(
              body ->
                  configActivationService
                      .activate(uuid(request, "apiId"), version, body.expectedDesiredVersion())
                      .flatMap(
                          result ->
                              ServerResponse.ok()
                                  .bodyValue(
                                      Map.of(
                                          "apiId",
                                          result.apiId(),
                                          "desiredVersion",
                                          result.desiredVersion(),
                                          "contentHash",
                                          result.contentHash(),
                                          "activatedAt",
                                          result.activatedAt().toString()))))
          .onErrorResume(ControlPlaneException.class, this::error)
          .onErrorResume(ex -> unexpectedError(request, ex));
    }

    Mono<ServerResponse> getConfigVersion(ServerRequest request) {
      long version = Long.parseLong(request.pathVariable("version"));
      return configVersionService
          .get(uuid(request, "apiId"), version)
          .flatMap(
              entity -> {
                try {
                  StoredRuntimeSnapshot snapshot =
                      RuntimeContentHasher.canonicalMapper()
                          .readValue(
                              entity.configSnapshot().asString(), StoredRuntimeSnapshot.class);
                  return ServerResponse.ok()
                      .bodyValue(ConfigVersionDetailResponse.from(entity, snapshot));
                } catch (Exception e) {
                  return error(ControlPlaneException.internal("Failed to read config snapshot"));
                }
              })
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    private Mono<ServerResponse> validationResponse(ValidationResult result) {
      if (result.valid()) {
        return ServerResponse.ok()
            .bodyValue(
                Map.of(
                    "valid", true,
                    "errors", List.of(),
                    "contentHash", result.contentHash(),
                    "summary", result.summary()));
      }
      return ServerResponse.status(422)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(Map.of("valid", false, "errors", result.errors()));
    }

    private Mono<ServerResponse> created(Object body) {
      return ServerResponse.status(201).bodyValue(body);
    }

    private Mono<ServerResponse> error(ControlPlaneException ex) {
      Map<String, Object> errorBody = new java.util.LinkedHashMap<>();
      errorBody.put("code", ex.code());
      errorBody.put("message", ex.getMessage());
      errorBody.put("details", ex.validationErrors());
      if (ex.existingConfigVersion() != null) {
        errorBody.put("existingVersion", ex.existingConfigVersion());
      }
      return ServerResponse.status(ex.status())
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(Map.of("error", errorBody));
    }

    private Mono<ServerResponse> unexpectedError(ServerRequest request, Throwable ex) {
      log.error(
          "Control plane request failed method={} path={} errorClass={} message={}",
          request.method(),
          request.path(),
          ex.getClass().getName(),
          ex.getMessage(),
          ex);
      return error(ControlPlaneException.internal("An internal control plane error occurred"));
    }

    private static UUID uuid(ServerRequest request, String name) {
      return UUID.fromString(request.pathVariable(name));
    }
  }

  record CreateProjectRequest(String name, String description) {}

  record CreateApiRequest(String name, String host, String basePath) {}

  record CreatePoolRequest(String name, String loadBalancing) {}

  record CreateTargetRequest(String url, Boolean enabled, Integer weight) {}

  record CreateRouteRequest(
      String name,
      String host,
      String pathPrefix,
      List<String> methods,
      UUID upstreamPoolId,
      Boolean enabled) {}

  record PublishConfigRequest(String message) {}

  record ActivateConfigRequest(Long expectedDesiredVersion) {}

  record ProjectResponse(
      UUID id, String name, String description, String createdAt, String updatedAt) {
    static ProjectResponse from(ProjectEntity entity) {
      return new ProjectResponse(
          entity.id(),
          entity.name(),
          entity.description(),
          entity.createdAt().toString(),
          entity.updatedAt().toString());
    }
  }

  record ApiResponse(
      UUID id,
      UUID projectId,
      String name,
      String host,
      String basePath,
      boolean enabled,
      Long desiredConfigVersion,
      String createdAt,
      String updatedAt) {
    static ApiResponse from(ApiEntity entity) {
      return new ApiResponse(
          entity.id(),
          entity.projectId(),
          entity.name(),
          entity.host(),
          entity.basePath(),
          entity.enabled(),
          entity.desiredConfigVersion(),
          entity.createdAt().toString(),
          entity.updatedAt().toString());
    }
  }

  record UpstreamPoolResponse(
      UUID id, UUID apiId, String name, String loadBalancing, String createdAt, String updatedAt) {
    static UpstreamPoolResponse from(UpstreamPoolEntity entity) {
      return new UpstreamPoolResponse(
          entity.id(),
          entity.apiId(),
          entity.name(),
          entity.loadBalancing(),
          entity.createdAt().toString(),
          entity.updatedAt().toString());
    }
  }

  record UpstreamTargetResponse(
      UUID id,
      UUID upstreamPoolId,
      String url,
      boolean enabled,
      int weight,
      String createdAt,
      String updatedAt) {
    static UpstreamTargetResponse from(UpstreamTargetEntity entity) {
      return new UpstreamTargetResponse(
          entity.id(),
          entity.upstreamPoolId(),
          entity.url(),
          entity.enabled(),
          entity.weight(),
          entity.createdAt().toString(),
          entity.updatedAt().toString());
    }
  }

  record RouteResponse(
      UUID id,
      UUID apiId,
      String name,
      String host,
      String pathPrefix,
      List<String> methods,
      UUID upstreamPoolId,
      boolean enabled,
      String createdAt,
      String updatedAt) {
    static RouteResponse from(RouteEntity entity) {
      return new RouteResponse(
          entity.id(),
          entity.apiId(),
          entity.name(),
          entity.host(),
          entity.pathPrefix(),
          List.of(entity.methods()),
          entity.upstreamPoolId(),
          entity.enabled(),
          entity.createdAt().toString(),
          entity.updatedAt().toString());
    }
  }

  record ConfigVersionResponse(
      UUID id, UUID apiId, long version, String contentHash, String message, String createdAt) {
    static ConfigVersionResponse from(ConfigVersionEntity entity) {
      return new ConfigVersionResponse(
          entity.id(),
          entity.apiId(),
          entity.version(),
          entity.contentHash(),
          entity.message(),
          entity.createdAt().toString());
    }
  }

  record ConfigVersionDetailResponse(
      UUID id,
      UUID apiId,
      long version,
      String contentHash,
      String message,
      String createdAt,
      StoredRuntimeSnapshot snapshot) {
    static ConfigVersionDetailResponse from(
        ConfigVersionEntity entity, StoredRuntimeSnapshot snapshot) {
      return new ConfigVersionDetailResponse(
          entity.id(),
          entity.apiId(),
          entity.version(),
          entity.contentHash(),
          entity.message(),
          entity.createdAt().toString(),
          snapshot);
    }
  }
}
