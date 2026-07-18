package com.autoapi.controlplane.events.webhooks;

import com.autoapi.controlplane.events.PlatformEventRecorder;
import com.autoapi.controlplane.events.PlatformEventTypes;
import com.autoapi.controlplane.events.RecordPlatformEventRequest;
import com.autoapi.controlplane.persistence.PlatformEventEntity;
import com.autoapi.controlplane.persistence.PlatformEventRepositoryCustom;
import com.autoapi.controlplane.persistence.WebhookDeliveryEntity;
import com.autoapi.controlplane.persistence.WebhookDeliveryRepositoryCustom;
import com.autoapi.controlplane.persistence.WebhookSubscriptionEntity;
import com.autoapi.controlplane.persistence.WebhookSubscriptionRepositoryCustom;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public final class WebhookDeliveryWorker {

  private static final Logger log = LoggerFactory.getLogger(WebhookDeliveryWorker.class);

  private final Disposable subscription;

  public WebhookDeliveryWorker(
      WebhooksProperties properties,
      WebhookDeliveryRepositoryCustom deliveryRepository,
      WebhookSubscriptionRepositoryCustom subscriptionRepository,
      PlatformEventRepositoryCustom eventRepository,
      WebhookSecretCrypto secretCrypto,
      WebhookHttpClient httpClient,
      PlatformEventRecorder eventRecorder,
      WebhooksMetrics metrics,
      ObjectMapper objectMapper,
      Clock clock) {
    if (!properties.enabled()) {
      this.subscription = null;
      return;
    }
    subscription =
        Flux.interval(properties.workerPollInterval())
            .concatMap(
                tick ->
                    deliveryRepository
                        .countPending()
                        .doOnNext(metrics::setQueueDepth)
                        .thenMany(
                            deliveryRepository.claimDueDeliveries(
                                properties.workerBatchSize(),
                                OffsetDateTime.now(clock.withZone(ZoneOffset.UTC))))
                        .flatMap(
                            delivery ->
                                deliver(
                                    delivery,
                                    deliveryRepository,
                                    subscriptionRepository,
                                    eventRepository,
                                    secretCrypto,
                                    httpClient,
                                    eventRecorder,
                                    metrics,
                                    objectMapper,
                                    properties,
                                    clock),
                            properties.workerConcurrency())
                        .then())
            .onErrorContinue(
                (error, obj) ->
                    log.warn("Webhook delivery worker iteration failed: {}", error.getMessage()))
            .subscribe();
  }

  @PreDestroy
  void shutdown() {
    if (subscription != null) {
      subscription.dispose();
    }
  }

  private static Mono<Void> deliver(
      WebhookDeliveryEntity delivery,
      WebhookDeliveryRepositoryCustom deliveryRepository,
      WebhookSubscriptionRepositoryCustom subscriptionRepository,
      PlatformEventRepositoryCustom eventRepository,
      WebhookSecretCrypto secretCrypto,
      WebhookHttpClient httpClient,
      PlatformEventRecorder eventRecorder,
      WebhooksMetrics metrics,
      ObjectMapper objectMapper,
      WebhooksProperties properties,
      Clock clock) {
    OffsetDateTime startedAt = OffsetDateTime.now(clock.withZone(ZoneOffset.UTC));
    int attemptNumber = delivery.attemptCount() + 1;
    log.info(
        "webhook_delivery_attempt_started deliveryId={} subscriptionId={} eventId={} attempt={}",
        delivery.id(),
        delivery.subscriptionId(),
        delivery.eventId(),
        attemptNumber);
    return subscriptionRepository
        .findBySubscriptionId(delivery.subscriptionId())
        .flatMap(
            subscription ->
                eventRepository
                    .findById(delivery.eventId())
                    .flatMap(
                        event ->
                            executeAttempt(
                                delivery,
                                subscription,
                                event,
                                attemptNumber,
                                startedAt,
                                deliveryRepository,
                                secretCrypto,
                                httpClient,
                                eventRecorder,
                                metrics,
                                objectMapper,
                                properties,
                                clock)));
  }

  private static Mono<Void> executeAttempt(
      WebhookDeliveryEntity delivery,
      WebhookSubscriptionEntity subscription,
      PlatformEventEntity event,
      int attemptNumber,
      OffsetDateTime startedAt,
      WebhookDeliveryRepositoryCustom deliveryRepository,
      WebhookSecretCrypto secretCrypto,
      WebhookHttpClient httpClient,
      PlatformEventRecorder eventRecorder,
      WebhooksMetrics metrics,
      ObjectMapper objectMapper,
      WebhooksProperties properties,
      Clock clock) {
    if (!subscription.enabled()) {
      return deliveryRepository
          .markDeadLettered(
              delivery.id(),
              attemptNumber,
              null,
              "SUBSCRIPTION_DISABLED",
              "Subscription disabled",
              OffsetDateTime.now(clock.withZone(ZoneOffset.UTC)))
          .then();
    }
    String secret = secretCrypto.decrypt(subscription.encryptedSecret());
    Instant timestamp = clock.instant();
    byte[] payloadBytes;
    try {
      payloadBytes = WebhookEnvelopeBuilder.canonicalPayloadBytes(event, objectMapper);
    } catch (Exception ex) {
      return handlePermanentFailure(
          delivery,
          subscription,
          event,
          attemptNumber,
          startedAt,
          null,
          "SERIALIZATION_ERROR",
          ex.getMessage(),
          deliveryRepository,
          eventRecorder,
          metrics,
          clock);
    }
    String signature = WebhookEnvelopeBuilder.sign(secret, timestamp, payloadBytes);
    Map<String, String> headers =
        WebhookEnvelopeBuilder.deliveryHeaders(
            event, delivery.id(), attemptNumber, timestamp, signature);
    Duration timeout = Duration.ofMillis(subscription.timeoutMs());
    long startNanos = System.nanoTime();
    return httpClient
        .post(subscription.url(), headers, payloadBytes, timeout)
        .flatMap(
            response -> {
              OffsetDateTime completedAt = OffsetDateTime.now(clock.withZone(ZoneOffset.UTC));
              int durationMs = (int) ((System.nanoTime() - startNanos) / 1_000_000L);
              return deliveryRepository
                  .insertAttempt(
                      delivery.id(),
                      attemptNumber,
                      startedAt,
                      completedAt,
                      durationMs,
                      response.statusCode(),
                      WebhookRetrySupport.isSuccessStatusCode(response.statusCode())
                          ? "SUCCEEDED"
                          : WebhookRetrySupport.isRetryableStatusCode(response.statusCode())
                              ? "RETRYABLE_FAILURE"
                              : "PERMANENT_FAILURE",
                      null,
                      response.bodyPreview())
                  .then(
                      handleResponse(
                          delivery,
                          subscription,
                          event,
                          attemptNumber,
                          response.statusCode(),
                          response.retryAfter(),
                          startedAt,
                          deliveryRepository,
                          eventRecorder,
                          metrics,
                          properties,
                          clock));
            })
        .onErrorResume(
            ex ->
                handleTransportFailure(
                        delivery,
                        subscription,
                        event,
                        attemptNumber,
                        startedAt,
                        ex,
                        deliveryRepository,
                        eventRecorder,
                        metrics,
                        properties,
                        clock)
                    .then());
  }

  private static Mono<Void> handleResponse(
      WebhookDeliveryEntity delivery,
      WebhookSubscriptionEntity subscription,
      PlatformEventEntity event,
      int attemptNumber,
      int statusCode,
      String retryAfterHeader,
      OffsetDateTime startedAt,
      WebhookDeliveryRepositoryCustom deliveryRepository,
      PlatformEventRecorder eventRecorder,
      WebhooksMetrics metrics,
      WebhooksProperties properties,
      Clock clock) {
    OffsetDateTime now = OffsetDateTime.now(clock.withZone(ZoneOffset.UTC));
    if (WebhookRetrySupport.isSuccessStatusCode(statusCode)) {
      metrics.recordDeliverySucceeded();
      log.info(
          "webhook_delivery_attempt_succeeded deliveryId={} attempt={} statusCode={}",
          delivery.id(),
          attemptNumber,
          statusCode);
      return deliveryRepository
          .markSucceeded(delivery.id(), statusCode, now, attemptNumber)
          .then(
              recordDeliveryEvent(
                  eventRecorder,
                  event,
                  PlatformEventTypes.WEBHOOK_DELIVERY_SUCCEEDED,
                  delivery,
                  statusCode));
    }
    if (WebhookRetrySupport.isRetryableStatusCode(statusCode)
        && attemptNumber < subscription.maxAttempts()) {
      Duration backoff =
          WebhookRetrySupport.parseRetryAfter(
                  retryAfterHeader, Duration.ofSeconds(subscription.maxBackoffSeconds()))
              .orElseGet(
                  () ->
                      WebhookRetrySupport.computeBackoff(
                          attemptNumber,
                          Duration.ofSeconds(subscription.initialBackoffSeconds()),
                          Duration.ofSeconds(subscription.maxBackoffSeconds())));
      OffsetDateTime nextAttempt =
          OffsetDateTime.ofInstant(
              WebhookRetrySupport.nextAttemptInstant(clock::instant, backoff), ZoneOffset.UTC);
      metrics.recordDeliveryRetry();
      log.info(
          "webhook_delivery_retry_scheduled deliveryId={} attempt={} nextAttemptAt={}",
          delivery.id(),
          attemptNumber,
          nextAttempt);
      return deliveryRepository
          .scheduleRetry(
              delivery.id(),
              attemptNumber,
              statusCode,
              "HTTP_" + statusCode,
              "Remote returned retryable status",
              nextAttempt,
              now)
          .then();
    }
    return handlePermanentFailure(
        delivery,
        subscription,
        event,
        attemptNumber,
        startedAt,
        statusCode,
        "HTTP_" + statusCode,
        "Delivery failed with status " + statusCode,
        deliveryRepository,
        eventRecorder,
        metrics,
        clock);
  }

  private static Mono<Void> handleTransportFailure(
      WebhookDeliveryEntity delivery,
      WebhookSubscriptionEntity subscription,
      PlatformEventEntity event,
      int attemptNumber,
      OffsetDateTime startedAt,
      Throwable ex,
      WebhookDeliveryRepositoryCustom deliveryRepository,
      PlatformEventRecorder eventRecorder,
      WebhooksMetrics metrics,
      WebhooksProperties properties,
      Clock clock) {
    String errorType =
        ex instanceof TimeoutException
            ? "TIMEOUT"
            : ex instanceof WebClientRequestException ? "CONNECTION_ERROR" : "DELIVERY_ERROR";
    boolean retryable =
        ex instanceof TimeoutException
            || ex instanceof WebClientRequestException
            || (ex instanceof WebClientResponseException response
                && WebhookRetrySupport.isRetryableStatusCode(response.getStatusCode().value()));
    OffsetDateTime now = OffsetDateTime.now(clock.withZone(ZoneOffset.UTC));
    Integer statusCode =
        ex instanceof WebClientResponseException response ? response.getStatusCode().value() : null;
    return deliveryRepository
        .insertAttempt(
            delivery.id(),
            attemptNumber,
            startedAt,
            now,
            (int) Duration.between(startedAt, now).toMillis(),
            statusCode,
            retryable ? "RETRYABLE_FAILURE" : "PERMANENT_FAILURE",
            errorType,
            truncate(ex.getMessage(), 512))
        .then(
            retryable && attemptNumber < subscription.maxAttempts()
                ? scheduleRetry(
                    delivery,
                    subscription,
                    attemptNumber,
                    statusCode,
                    errorType,
                    deliveryRepository,
                    metrics,
                    clock)
                : handlePermanentFailure(
                    delivery,
                    subscription,
                    event,
                    attemptNumber,
                    startedAt,
                    statusCode,
                    errorType,
                    ex.getMessage(),
                    deliveryRepository,
                    eventRecorder,
                    metrics,
                    clock));
  }

  private static Mono<Void> scheduleRetry(
      WebhookDeliveryEntity delivery,
      WebhookSubscriptionEntity subscription,
      int attemptNumber,
      Integer statusCode,
      String errorType,
      WebhookDeliveryRepositoryCustom deliveryRepository,
      WebhooksMetrics metrics,
      Clock clock) {
    Duration backoff =
        WebhookRetrySupport.computeBackoff(
            attemptNumber,
            Duration.ofSeconds(subscription.initialBackoffSeconds()),
            Duration.ofSeconds(subscription.maxBackoffSeconds()));
    OffsetDateTime nextAttempt =
        OffsetDateTime.ofInstant(
            WebhookRetrySupport.nextAttemptInstant(clock::instant, backoff), ZoneOffset.UTC);
    metrics.recordDeliveryRetry();
    return deliveryRepository
        .scheduleRetry(
            delivery.id(),
            attemptNumber,
            statusCode == null ? 0 : statusCode,
            errorType,
            "Transport failure",
            nextAttempt,
            OffsetDateTime.now(clock.withZone(ZoneOffset.UTC)))
        .then();
  }

  private static Mono<Void> handlePermanentFailure(
      WebhookDeliveryEntity delivery,
      WebhookSubscriptionEntity subscription,
      PlatformEventEntity event,
      int attemptNumber,
      OffsetDateTime startedAt,
      Integer statusCode,
      String errorType,
      String errorSummary,
      WebhookDeliveryRepositoryCustom deliveryRepository,
      PlatformEventRecorder eventRecorder,
      WebhooksMetrics metrics,
      Clock clock) {
    OffsetDateTime now = OffsetDateTime.now(clock.withZone(ZoneOffset.UTC));
    metrics.recordDeadLetter();
    log.warn(
        "webhook_delivery_dead_lettered deliveryId={} attempts={}", delivery.id(), attemptNumber);
    return deliveryRepository
        .markDeadLettered(
            delivery.id(), attemptNumber, statusCode, errorType, truncate(errorSummary, 512), now)
        .then(
            recordDeliveryEvent(
                eventRecorder,
                event,
                PlatformEventTypes.WEBHOOK_DELIVERY_DEAD_LETTERED,
                delivery,
                statusCode));
  }

  private static Mono<Void> recordDeliveryEvent(
      PlatformEventRecorder eventRecorder,
      PlatformEventEntity event,
      String eventType,
      WebhookDeliveryEntity delivery,
      Integer statusCode) {
    return eventRecorder
        .record(
            RecordPlatformEventRequest.of(
                eventType,
                event.projectId(),
                event.apiId(),
                "WEBHOOK_DELIVERY",
                delivery.id().toString(),
                com.autoapi.controlplane.events.EventContext.system(
                    "webhook-worker", "WEBHOOK_WORKER"),
                Map.of(
                    "deliveryId",
                    delivery.id().toString(),
                    "subscriptionId",
                    delivery.subscriptionId().toString(),
                    "eventId",
                    event.id().toString(),
                    "statusCode",
                    statusCode == null ? 0 : statusCode)))
        .then();
  }

  private static String truncate(String value, int max) {
    if (value == null) {
      return null;
    }
    return value.length() <= max ? value : value.substring(0, max);
  }
}
