package com.autoapi.controlplane.rollout;

import com.autoapi.controlplane.api.ControlPlaneException;
import com.autoapi.controlplane.events.EventContext;
import com.autoapi.controlplane.rollout.RolloutStageCalculator.StageDefinitionInput;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.OffsetDateTime;
import java.util.List;
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
public class RuntimeRolloutManagementRouter {

  @Bean
  @Order(16)
  RouterFunction<ServerResponse> runtimeRolloutManagementRoutes(
      RuntimeRolloutService rolloutService) {
    Handler handler = new Handler(rolloutService);
    return RouterFunctions.route()
        .path(
            "/api/v1/management/projects/{projectId}/rollouts",
            builder ->
                builder
                    .POST("", handler::createRollout)
                    .POST("/preview", handler::previewRollout)
                    .GET("", handler::listRollouts)
                    .GET("/{rolloutId}", handler::getRollout)
                    .GET("/{rolloutId}/assignments", handler::listAssignments)
                    .POST("/{rolloutId}/preview", handler::previewExistingRollout)
                    .POST("/{rolloutId}/start", handler::startRollout)
                    .POST("/{rolloutId}/pause", handler::pauseRollout)
                    .POST("/{rolloutId}/resume", handler::resumeRollout)
                    .POST("/{rolloutId}/advance", handler::advanceRollout)
                    .POST("/{rolloutId}/cancel", handler::cancelRollout)
                    .POST("/{rolloutId}/rollback", handler::rollbackRollout))
        .build();
  }

  static final class Handler {

    private final RuntimeRolloutService rolloutService;

    Handler(RuntimeRolloutService rolloutService) {
      this.rolloutService = rolloutService;
    }

    Mono<ServerResponse> createRollout(ServerRequest request) {
      UUID projectId = uuid(request, "projectId");
      return request
          .bodyToMono(CreateRolloutRequest.class)
          .flatMap(
              body ->
                  rolloutService
                      .createDraft(
                          projectId,
                          body.gatewayGroupId(),
                          body.targetVersion(),
                          body.strategy(),
                          body.progressionMode(),
                          body.autoRollbackOnFailure(),
                          body.cancelBehavior(),
                          body.stages(),
                          context(request))
                      .flatMap(view -> ServerResponse.ok().bodyValue(view)))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> previewRollout(ServerRequest request) {
      UUID projectId = uuid(request, "projectId");
      return request
          .bodyToMono(PreviewRolloutRequest.class)
          .flatMap(
              body ->
                  rolloutService
                      .preview(
                          projectId,
                          body.gatewayGroupId(),
                          body.targetVersion(),
                          body.strategy(),
                          body.stages())
                      .flatMap(preview -> ServerResponse.ok().bodyValue(preview)))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> listRollouts(ServerRequest request) {
      UUID projectId = uuid(request, "projectId");
      UUID gatewayGroupId = queryUuid(request, "gatewayGroupId");
      String status = request.queryParam("status").orElse(null);
      String strategy = request.queryParam("strategy").orElse(null);
      Long sourceVersion = request.queryParam("sourceVersion").map(Long::parseLong).orElse(null);
      Long targetVersion = request.queryParam("targetVersion").map(Long::parseLong).orElse(null);
      OffsetDateTime createdAfter = parseTime(request, "createdAfter");
      OffsetDateTime createdBefore = parseTime(request, "createdBefore");
      int limit = request.queryParam("limit").map(Integer::parseInt).orElse(50);
      int offset = request.queryParam("offset").map(Integer::parseInt).orElse(0);
      return rolloutService
          .list(
              projectId,
              gatewayGroupId,
              status,
              strategy,
              sourceVersion,
              targetVersion,
              createdAfter,
              createdBefore,
              limit,
              offset)
          .collectList()
          .flatMap(list -> ServerResponse.ok().bodyValue(list))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> getRollout(ServerRequest request) {
      return rolloutService
          .getDetail(uuid(request, "projectId"), uuid(request, "rolloutId"))
          .flatMap(detail -> ServerResponse.ok().bodyValue(detail))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> listAssignments(ServerRequest request) {
      int limit = request.queryParam("limit").map(Integer::parseInt).orElse(50);
      int offset = request.queryParam("offset").map(Integer::parseInt).orElse(0);
      return rolloutService
          .listAssignments(uuid(request, "projectId"), uuid(request, "rolloutId"), limit, offset)
          .collectList()
          .flatMap(list -> ServerResponse.ok().bodyValue(list))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> previewExistingRollout(ServerRequest request) {
      return rolloutService
          .previewExisting(uuid(request, "projectId"), uuid(request, "rolloutId"))
          .flatMap(preview -> ServerResponse.ok().bodyValue(preview))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> startRollout(ServerRequest request) {
      return rolloutService
          .start(uuid(request, "projectId"), uuid(request, "rolloutId"), context(request))
          .flatMap(view -> ServerResponse.ok().bodyValue(view))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> pauseRollout(ServerRequest request) {
      return rolloutService
          .pause(uuid(request, "projectId"), uuid(request, "rolloutId"), context(request))
          .flatMap(view -> ServerResponse.ok().bodyValue(view))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> resumeRollout(ServerRequest request) {
      return rolloutService
          .resume(uuid(request, "projectId"), uuid(request, "rolloutId"), context(request))
          .flatMap(view -> ServerResponse.ok().bodyValue(view))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> advanceRollout(ServerRequest request) {
      return rolloutService
          .advance(uuid(request, "projectId"), uuid(request, "rolloutId"), context(request))
          .flatMap(view -> ServerResponse.ok().bodyValue(view))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> cancelRollout(ServerRequest request) {
      return rolloutService
          .cancel(uuid(request, "projectId"), uuid(request, "rolloutId"), context(request))
          .flatMap(view -> ServerResponse.ok().bodyValue(view))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> rollbackRollout(ServerRequest request) {
      return rolloutService
          .rollback(uuid(request, "projectId"), uuid(request, "rolloutId"), context(request))
          .flatMap(view -> ServerResponse.ok().bodyValue(view))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    private static EventContext context(ServerRequest request) {
      return EventContext.managementApi(request.headers().firstHeader("X-Request-ID"));
    }

    private static UUID uuid(ServerRequest request, String name) {
      return UUID.fromString(request.pathVariable(name));
    }

    private static UUID queryUuid(ServerRequest request, String name) {
      return request.queryParam(name).map(UUID::fromString).orElse(null);
    }

    private static OffsetDateTime parseTime(ServerRequest request, String name) {
      return request.queryParam(name).map(OffsetDateTime::parse).orElse(null);
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

  @JsonIgnoreProperties(ignoreUnknown = true)
  record CreateRolloutRequest(
      UUID gatewayGroupId,
      long targetVersion,
      String strategy,
      String progressionMode,
      Boolean autoRollbackOnFailure,
      String cancelBehavior,
      List<StageDefinitionInput> stages) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record PreviewRolloutRequest(
      UUID gatewayGroupId,
      long targetVersion,
      String strategy,
      List<StageDefinitionInput> stages) {}
}
