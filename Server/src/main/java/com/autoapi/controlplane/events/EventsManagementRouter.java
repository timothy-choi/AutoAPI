package com.autoapi.controlplane.events;

import com.autoapi.controlplane.api.ControlPlaneException;
import java.time.OffsetDateTime;
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
    name = {"autoapi.controlplane.enabled", "autoapi.events.enabled"},
    havingValue = "true",
    matchIfMissing = true)
public class EventsManagementRouter {

  @Bean
  @Order(13)
  RouterFunction<ServerResponse> eventsManagementRoutes(PlatformEventQueryService queryService) {
    Handler handler = new Handler(queryService);
    return RouterFunctions.route()
        .path(
            "/api/v1/management",
            builder ->
                builder
                    .GET("/events", handler::listEvents)
                    .GET("/events/{eventId}", handler::getEvent)
                    .GET("/audit", handler::listAudit))
        .build();
  }

  static final class Handler {

    private final PlatformEventQueryService queryService;

    Handler(PlatformEventQueryService queryService) {
      this.queryService = queryService;
    }

    Mono<ServerResponse> listEvents(ServerRequest request) {
      UUID projectId = uuidParam(request, "projectId");
      UUID apiId = uuidParam(request, "apiId");
      String eventType = request.queryParam("eventType").orElse(null);
      String resourceType = request.queryParam("resourceType").orElse(null);
      String resourceId = request.queryParam("resourceId").orElse(null);
      String correlationId = request.queryParam("correlationId").orElse(null);
      Long afterSequence = request.queryParam("afterSequence").map(Long::parseLong).orElse(null);
      OffsetDateTime occurredAfter = parseTime(request, "occurredAfter");
      OffsetDateTime occurredBefore = parseTime(request, "occurredBefore");
      int limit = request.queryParam("limit").map(Integer::parseInt).orElse(50);
      return queryService
          .list(
              projectId,
              apiId,
              eventType,
              resourceType,
              resourceId,
              correlationId,
              afterSequence,
              occurredAfter,
              occurredBefore,
              limit)
          .collectList()
          .flatMap(list -> ServerResponse.ok().bodyValue(list))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> getEvent(ServerRequest request) {
      return queryService
          .get(UUID.fromString(request.pathVariable("eventId")))
          .flatMap(event -> ServerResponse.ok().bodyValue(event))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> listAudit(ServerRequest request) {
      UUID projectId = uuidParam(request, "projectId");
      if (projectId == null) {
        return error(ControlPlaneException.invalidRequest("projectId is required"));
      }
      Long afterSequence = request.queryParam("afterSequence").map(Long::parseLong).orElse(null);
      int limit = request.queryParam("limit").map(Integer::parseInt).orElse(50);
      return queryService
          .listAudit(projectId, afterSequence, limit)
          .collectList()
          .flatMap(list -> ServerResponse.ok().bodyValue(list))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    private static UUID uuidParam(ServerRequest request, String name) {
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
}
