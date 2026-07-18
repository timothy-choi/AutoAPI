package com.autoapi.controlplane.events.webhooks;

import com.autoapi.controlplane.events.PlatformEventTypes;
import com.autoapi.controlplane.persistence.PlatformEventEntity;
import com.autoapi.controlplane.persistence.PlatformEventRepositoryCustom;
import com.autoapi.controlplane.persistence.WebhookDeliveryRepositoryCustom;
import com.autoapi.controlplane.persistence.WebhookSubscriptionEntity;
import com.autoapi.controlplane.persistence.WebhookSubscriptionRepositoryCustom;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public final class EventOutboxDispatcher {

  private static final Logger log = LoggerFactory.getLogger(EventOutboxDispatcher.class);

  private final Disposable subscription;
  private final ObjectMapper objectMapper;

  public EventOutboxDispatcher(
      com.autoapi.controlplane.events.EventsProperties eventsProperties,
      WebhooksProperties webhooksProperties,
      PlatformEventRepositoryCustom eventRepository,
      WebhookSubscriptionRepositoryCustom subscriptionRepository,
      WebhookDeliveryRepositoryCustom deliveryRepository,
      ObjectMapper objectMapper,
      Clock clock) {
    this.objectMapper = objectMapper;
    if (!eventsProperties.enabled() || !webhooksProperties.enabled()) {
      this.subscription = null;
      return;
    }
    subscription =
        Flux.interval(eventsProperties.outboxPollInterval())
            .concatMap(
                tick ->
                    eventRepository
                        .claimPendingDispatch(eventsProperties.outboxBatchSize())
                        .concatMap(
                            event ->
                                dispatchEvent(
                                    event,
                                    subscriptionRepository,
                                    deliveryRepository,
                                    clock,
                                    objectMapper))
                        .then())
            .onErrorContinue(
                (error, obj) ->
                    log.warn("Event outbox dispatch iteration failed: {}", error.getMessage()))
            .subscribe();
  }

  @PreDestroy
  void shutdown() {
    if (subscription != null) {
      subscription.dispose();
    }
  }

  private static Mono<Void> dispatchEvent(
      PlatformEventEntity event,
      WebhookSubscriptionRepositoryCustom subscriptionRepository,
      WebhookDeliveryRepositoryCustom deliveryRepository,
      Clock clock,
      ObjectMapper objectMapper) {
    if (PlatformEventTypes.NON_DELIVERABLE_EVENT_TYPES.contains(event.eventType())) {
      log.debug(
          "event_outbox_skipped eventId={} eventType={} reason=non_deliverable",
          event.id(),
          event.eventType());
      return Mono.empty();
    }
    if (event.projectId() == null) {
      return Mono.empty();
    }
    OffsetDateTime now = OffsetDateTime.now(clock.withZone(ZoneOffset.UTC));
    return subscriptionRepository
        .listEnabledForProject(event.projectId())
        .filter(
            subscription ->
                WebhookEventFilterMatcher.matches(
                    event,
                    subscription.eventFilters(),
                    subscription.resourceFilters(),
                    objectMapper))
        .concatMap(subscription -> createDelivery(subscription, event, deliveryRepository, now))
        .then()
        .doOnSuccess(
            ignored ->
                log.info(
                    "event_outbox_dispatched eventId={} eventType={} projectId={}",
                    event.id(),
                    event.eventType(),
                    event.projectId()));
  }

  private static Mono<Void> createDelivery(
      WebhookSubscriptionEntity subscription,
      PlatformEventEntity event,
      WebhookDeliveryRepositoryCustom deliveryRepository,
      OffsetDateTime now) {
    return deliveryRepository
        .createPending(subscription.id(), event.id(), subscription.secretVersion(), now, null)
        .doOnSuccess(
            delivery ->
                log.info(
                    "webhook_delivery_created deliveryId={} subscriptionId={} eventId={}",
                    delivery.id(),
                    subscription.id(),
                    event.id()))
        .then();
  }
}
