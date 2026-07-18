package com.autoapi.controlplane.events.webhooks;

import com.autoapi.controlplane.persistence.PlatformEventRepositoryCustom;
import com.autoapi.controlplane.persistence.WebhookDeliveryEntity;
import com.autoapi.controlplane.persistence.WebhookDeliveryRepositoryCustom;
import com.autoapi.controlplane.persistence.WebhookSubscriptionRepositoryCustom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@ConditionalOnProperty(
    name = {"autoapi.controlplane.enabled", "autoapi.webhooks.enabled"},
    havingValue = "true",
    matchIfMissing = true)
public class WebhookDeliveryQueryService {

  private static final int MAX_PAGE_SIZE = 200;

  private final WebhookDeliveryRepositoryCustom deliveryRepository;
  private final WebhookSubscriptionRepositoryCustom subscriptionRepository;
  private final PlatformEventRepositoryCustom eventRepository;

  public WebhookDeliveryQueryService(
      WebhookDeliveryRepositoryCustom deliveryRepository,
      WebhookSubscriptionRepositoryCustom subscriptionRepository,
      PlatformEventRepositoryCustom eventRepository) {
    this.deliveryRepository = deliveryRepository;
    this.subscriptionRepository = subscriptionRepository;
    this.eventRepository = eventRepository;
  }

  public Flux<WebhookDeliveryView> list(
      UUID projectId,
      UUID subscriptionId,
      UUID eventId,
      String status,
      OffsetDateTime createdAfter,
      OffsetDateTime createdBefore,
      int limit) {
    int bounded = Math.min(Math.max(limit, 1), MAX_PAGE_SIZE);
    return deliveryRepository
        .list(projectId, subscriptionId, eventId, status, createdAfter, createdBefore, bounded)
        .map(WebhookDeliveryView::from);
  }

  public Mono<WebhookDeliveryDetailView> get(UUID projectId, UUID deliveryId) {
    return deliveryRepository
        .findById(projectId, deliveryId)
        .switchIfEmpty(
            Mono.error(
                com.autoapi.controlplane.api.ControlPlaneException.notFound(
                    "Webhook delivery was not found")))
        .flatMap(
            delivery ->
                deliveryRepository
                    .listAttempts(deliveryId)
                    .collectList()
                    .flatMap(
                        attempts ->
                            eventRepository
                                .findById(delivery.eventId())
                                .map(
                                    event ->
                                        new WebhookDeliveryDetailView(
                                            WebhookDeliveryView.from(delivery),
                                            event.eventType(),
                                            attempts.stream()
                                                .map(WebhookDeliveryAttemptView::from)
                                                .toList()))));
  }

  public Mono<UUID> replay(UUID projectId, UUID deliveryId) {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    return deliveryRepository
        .findById(projectId, deliveryId)
        .switchIfEmpty(
            Mono.error(
                com.autoapi.controlplane.api.ControlPlaneException.notFound(
                    "Webhook delivery was not found")))
        .flatMap(
            delivery ->
                deliveryRepository
                    .resetForReplay(deliveryId, now)
                    .switchIfEmpty(
                        Mono.error(
                            com.autoapi.controlplane.api.ControlPlaneException.invalidRequest(
                                "Only dead-lettered deliveries can be replayed")))
                    .map(WebhookDeliveryEntity::id));
  }

  public record WebhookDeliveryView(
      UUID id,
      UUID subscriptionId,
      UUID eventId,
      String status,
      int attemptCount,
      OffsetDateTime nextAttemptAt,
      OffsetDateTime lastAttemptAt,
      OffsetDateTime deliveredAt,
      OffsetDateTime deadLetteredAt,
      Integer lastStatusCode,
      String lastErrorType,
      String lastErrorSummary,
      UUID replayOfDeliveryId,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt) {

    static WebhookDeliveryView from(WebhookDeliveryEntity entity) {
      return new WebhookDeliveryView(
          entity.id(),
          entity.subscriptionId(),
          entity.eventId(),
          entity.status(),
          entity.attemptCount(),
          entity.nextAttemptAt(),
          entity.lastAttemptAt(),
          entity.deliveredAt(),
          entity.deadLetteredAt(),
          entity.lastStatusCode(),
          entity.lastErrorType(),
          entity.lastErrorSummary(),
          entity.replayOfDeliveryId(),
          entity.createdAt(),
          entity.updatedAt());
    }
  }

  public record WebhookDeliveryAttemptView(
      UUID id,
      int attemptNumber,
      OffsetDateTime startedAt,
      OffsetDateTime completedAt,
      Integer durationMs,
      Integer statusCode,
      String result,
      String errorType,
      String responseBodyPreview) {

    static WebhookDeliveryAttemptView from(
        com.autoapi.controlplane.persistence.WebhookDeliveryAttemptEntity entity) {
      return new WebhookDeliveryAttemptView(
          entity.id(),
          entity.attemptNumber(),
          entity.startedAt(),
          entity.completedAt(),
          entity.durationMs(),
          entity.statusCode(),
          entity.result(),
          entity.errorType(),
          entity.responseBodyPreview());
    }
  }

  public record WebhookDeliveryDetailView(
      WebhookDeliveryView delivery,
      String eventType,
      java.util.List<WebhookDeliveryAttemptView> attempts) {}
}
