package com.autoapi.controlplane.events.webhooks;

import com.autoapi.controlplane.api.ControlPlaneException;
import com.autoapi.controlplane.events.EventContext;
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
    name = {"autoapi.controlplane.enabled", "autoapi.webhooks.enabled"},
    havingValue = "true",
    matchIfMissing = true)
public class WebhookManagementRouter {

  @Bean
  @Order(14)
  RouterFunction<ServerResponse> webhookManagementRoutes(
      WebhookSubscriptionService subscriptionService,
      WebhookDeliveryQueryService deliveryQueryService) {
    Handler handler = new Handler(subscriptionService, deliveryQueryService);
    return RouterFunctions.route()
        .path(
            "/api/v1/management/projects/{projectId}",
            builder ->
                builder
                    .POST("/webhooks", handler::createWebhook)
                    .GET("/webhooks", handler::listWebhooks)
                    .GET("/webhooks/{webhookId}", handler::getWebhook)
                    .PATCH("/webhooks/{webhookId}", handler::updateWebhook)
                    .DELETE("/webhooks/{webhookId}", handler::deleteWebhook)
                    .POST("/webhooks/{webhookId}/rotate-secret", handler::rotateSecret)
                    .POST("/webhooks/{webhookId}/test", handler::testWebhook)
                    .GET("/webhook-deliveries", handler::listDeliveries)
                    .GET("/webhook-deliveries/{deliveryId}", handler::getDelivery)
                    .POST("/webhook-deliveries/{deliveryId}/replay", handler::replayDelivery))
        .build();
  }

  static final class Handler {

    private final WebhookSubscriptionService subscriptionService;
    private final WebhookDeliveryQueryService deliveryQueryService;

    Handler(
        WebhookSubscriptionService subscriptionService,
        WebhookDeliveryQueryService deliveryQueryService) {
      this.subscriptionService = subscriptionService;
      this.deliveryQueryService = deliveryQueryService;
    }

    Mono<ServerResponse> createWebhook(ServerRequest request) {
      UUID projectId = uuid(request, "projectId");
      return request
          .bodyToMono(CreateWebhookRequest.class)
          .flatMap(
              body ->
                  subscriptionService
                      .create(
                          projectId,
                          body.name(),
                          body.description(),
                          body.url(),
                          body.eventFilters(),
                          body.resourceFilters(),
                          body.maxAttempts(),
                          body.initialBackoffSeconds(),
                          body.maxBackoffSeconds(),
                          body.timeoutMs(),
                          context(request))
                      .flatMap(
                          result ->
                              ServerResponse.ok()
                                  .bodyValue(
                                      Map.of(
                                          "subscription",
                                          WebhookSubscriptionService.WebhookSubscriptionView.from(
                                              result.subscription()),
                                          "secret",
                                          result.secret()))))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> listWebhooks(ServerRequest request) {
      return subscriptionService
          .list(uuid(request, "projectId"))
          .collectList()
          .flatMap(list -> ServerResponse.ok().bodyValue(list))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> getWebhook(ServerRequest request) {
      return subscriptionService
          .get(uuid(request, "projectId"), uuid(request, "webhookId"))
          .flatMap(view -> ServerResponse.ok().bodyValue(view))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> updateWebhook(ServerRequest request) {
      UUID projectId = uuid(request, "projectId");
      UUID webhookId = uuid(request, "webhookId");
      return request
          .bodyToMono(UpdateWebhookRequest.class)
          .flatMap(
              body ->
                  subscriptionService
                      .update(
                          projectId,
                          webhookId,
                          body.name(),
                          body.description(),
                          body.url(),
                          body.enabled(),
                          body.eventFilters(),
                          body.resourceFilters(),
                          body.maxAttempts(),
                          body.initialBackoffSeconds(),
                          body.maxBackoffSeconds(),
                          body.timeoutMs(),
                          context(request))
                      .flatMap(view -> ServerResponse.ok().bodyValue(view)))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> deleteWebhook(ServerRequest request) {
      return subscriptionService
          .delete(uuid(request, "projectId"), uuid(request, "webhookId"), context(request))
          .then(ServerResponse.noContent().build())
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> rotateSecret(ServerRequest request) {
      return subscriptionService
          .rotateSecret(uuid(request, "projectId"), uuid(request, "webhookId"), context(request))
          .flatMap(secret -> ServerResponse.ok().bodyValue(Map.of("secret", secret)))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> testWebhook(ServerRequest request) {
      return subscriptionService
          .testDelivery(uuid(request, "projectId"), uuid(request, "webhookId"), context(request))
          .flatMap(
              deliveryId -> ServerResponse.accepted().bodyValue(Map.of("deliveryId", deliveryId)))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> listDeliveries(ServerRequest request) {
      UUID projectId = uuid(request, "projectId");
      UUID subscriptionId = queryUuid(request, "subscriptionId");
      UUID eventId = queryUuid(request, "eventId");
      String status = request.queryParam("status").orElse(null);
      OffsetDateTime createdAfter = parseTime(request, "createdAfter");
      OffsetDateTime createdBefore = parseTime(request, "createdBefore");
      int limit = request.queryParam("limit").map(Integer::parseInt).orElse(50);
      return deliveryQueryService
          .list(projectId, subscriptionId, eventId, status, createdAfter, createdBefore, limit)
          .collectList()
          .flatMap(list -> ServerResponse.ok().bodyValue(list))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> getDelivery(ServerRequest request) {
      return deliveryQueryService
          .get(uuid(request, "projectId"), uuid(request, "deliveryId"))
          .flatMap(detail -> ServerResponse.ok().bodyValue(detail))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> replayDelivery(ServerRequest request) {
      return deliveryQueryService
          .replay(uuid(request, "projectId"), uuid(request, "deliveryId"))
          .flatMap(
              deliveryId -> ServerResponse.accepted().bodyValue(Map.of("deliveryId", deliveryId)))
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

  record CreateWebhookRequest(
      String name,
      String description,
      String url,
      List<String> eventFilters,
      List<String> resourceFilters,
      Integer maxAttempts,
      Integer initialBackoffSeconds,
      Integer maxBackoffSeconds,
      Integer timeoutMs) {}

  record UpdateWebhookRequest(
      String name,
      String description,
      String url,
      Boolean enabled,
      List<String> eventFilters,
      List<String> resourceFilters,
      Integer maxAttempts,
      Integer initialBackoffSeconds,
      Integer maxBackoffSeconds,
      Integer timeoutMs) {}
}
