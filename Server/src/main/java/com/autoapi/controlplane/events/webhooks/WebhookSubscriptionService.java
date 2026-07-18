package com.autoapi.controlplane.events.webhooks;

import com.autoapi.controlplane.api.ControlPlaneException;
import com.autoapi.controlplane.events.EventContext;
import com.autoapi.controlplane.events.PlatformEventRecorder;
import com.autoapi.controlplane.events.PlatformEventTypes;
import com.autoapi.controlplane.events.RecordPlatformEventRequest;
import com.autoapi.controlplane.persistence.WebhookDeliveryEntity;
import com.autoapi.controlplane.persistence.WebhookDeliveryRepositoryCustom;
import com.autoapi.controlplane.persistence.WebhookSubscriptionEntity;
import com.autoapi.controlplane.persistence.WebhookSubscriptionRepositoryCustom;
import com.autoapi.controlplane.project.ProjectService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@ConditionalOnProperty(
    name = {"autoapi.controlplane.enabled", "autoapi.webhooks.enabled"},
    havingValue = "true",
    matchIfMissing = true)
public class WebhookSubscriptionService {

  private static final Logger log = LoggerFactory.getLogger(WebhookSubscriptionService.class);

  private final WebhookSubscriptionRepositoryCustom repository;
  private final WebhookDeliveryRepositoryCustom deliveryRepository;
  private final PlatformEventRecorder eventRecorder;
  private final WebhookSecretCrypto secretCrypto;
  private final WebhooksProperties properties;
  private final ProjectService projectService;
  private final ObjectMapper objectMapper;

  public WebhookSubscriptionService(
      WebhookSubscriptionRepositoryCustom repository,
      WebhookDeliveryRepositoryCustom deliveryRepository,
      PlatformEventRecorder eventRecorder,
      WebhookSecretCrypto secretCrypto,
      WebhooksProperties properties,
      ProjectService projectService,
      ObjectMapper objectMapper) {
    this.repository = repository;
    this.deliveryRepository = deliveryRepository;
    this.eventRecorder = eventRecorder;
    this.secretCrypto = secretCrypto;
    this.properties = properties;
    this.projectService = projectService;
    this.objectMapper = objectMapper;
  }

  public record CreateResult(WebhookSubscriptionEntity subscription, String secret) {}

  @Transactional(transactionManager = "connectionFactoryTransactionManager")
  public Mono<CreateResult> create(
      UUID projectId,
      String name,
      String description,
      String url,
      List<String> eventFilters,
      List<String> resourceFilters,
      Integer maxAttempts,
      Integer initialBackoffSeconds,
      Integer maxBackoffSeconds,
      Integer timeoutMs,
      EventContext context) {
    validateFilters(eventFilters);
    WebhookUrlValidator.validate(url, properties.security());
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    return projectService
        .get(projectId)
        .then(
            repository
                .countByProject(projectId)
                .flatMap(
                    count -> {
                      if (count >= properties.maxSubscriptionsPerProject()) {
                        return Mono.error(
                            ControlPlaneException.invalidRequest(
                                "Maximum webhook subscriptions per project exceeded"));
                      }
                      String plaintextSecret = WebhookSecretCrypto.generatePlaintextSecret();
                      byte[] encrypted = secretCrypto.encrypt(plaintextSecret);
                      return repository
                          .insert(
                              projectId,
                              name,
                              description,
                              url,
                              eventFilters == null ? List.of() : eventFilters,
                              resourceFilters == null ? List.of() : resourceFilters,
                              encrypted,
                              maxAttempts != null ? maxAttempts : properties.defaultMaxAttempts(),
                              initialBackoffSeconds != null
                                  ? initialBackoffSeconds
                                  : (int) properties.defaultInitialBackoff().getSeconds(),
                              maxBackoffSeconds != null
                                  ? maxBackoffSeconds
                                  : (int) properties.defaultMaxBackoff().getSeconds(),
                              timeoutMs != null
                                  ? timeoutMs
                                  : (int) properties.defaultTimeout().toMillis(),
                              now)
                          .flatMap(
                              subscription ->
                                  recordSubscriptionEvent(
                                          subscription,
                                          PlatformEventTypes.WEBHOOK_SUBSCRIPTION_CREATED,
                                          context)
                                      .thenReturn(new CreateResult(subscription, plaintextSecret)))
                          .doOnSuccess(
                              result ->
                                  log.info(
                                      "webhook_subscription_created subscriptionId={} projectId={}",
                                      result.subscription().id(),
                                      projectId));
                    }))
        .onErrorMap(
            DataIntegrityViolationException.class,
            ex -> ControlPlaneException.conflict("Webhook subscription name already exists"));
  }

  public Flux<WebhookSubscriptionView> list(UUID projectId) {
    return repository.listByProject(projectId).map(WebhookSubscriptionView::from);
  }

  public Mono<WebhookSubscriptionView> get(UUID projectId, UUID subscriptionId) {
    return repository
        .findById(projectId, subscriptionId)
        .map(WebhookSubscriptionView::from)
        .switchIfEmpty(
            Mono.error(ControlPlaneException.notFound("Webhook subscription was not found")));
  }

  @Transactional(transactionManager = "connectionFactoryTransactionManager")
  public Mono<WebhookSubscriptionView> update(
      UUID projectId,
      UUID subscriptionId,
      String name,
      String description,
      String url,
      Boolean enabled,
      List<String> eventFilters,
      List<String> resourceFilters,
      Integer maxAttempts,
      Integer initialBackoffSeconds,
      Integer maxBackoffSeconds,
      Integer timeoutMs,
      EventContext context) {
    if (eventFilters != null) {
      validateFilters(eventFilters);
    }
    if (url != null) {
      WebhookUrlValidator.validate(url, properties.security());
    }
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    return repository
        .update(
            projectId,
            subscriptionId,
            name,
            description,
            url,
            enabled,
            eventFilters,
            resourceFilters,
            maxAttempts,
            initialBackoffSeconds,
            maxBackoffSeconds,
            timeoutMs,
            now)
        .switchIfEmpty(
            Mono.error(ControlPlaneException.notFound("Webhook subscription was not found")))
        .flatMap(
            subscription ->
                recordSubscriptionEvent(
                        subscription,
                        enabled != null && !enabled
                            ? PlatformEventTypes.WEBHOOK_SUBSCRIPTION_DISABLED
                            : PlatformEventTypes.WEBHOOK_SUBSCRIPTION_UPDATED,
                        context)
                    .thenReturn(WebhookSubscriptionView.from(subscription)));
  }

  @Transactional(transactionManager = "connectionFactoryTransactionManager")
  public Mono<Void> delete(UUID projectId, UUID subscriptionId, EventContext context) {
    return repository
        .findById(projectId, subscriptionId)
        .switchIfEmpty(
            Mono.error(ControlPlaneException.notFound("Webhook subscription was not found")))
        .flatMap(
            subscription ->
                recordSubscriptionEvent(
                        subscription, PlatformEventTypes.WEBHOOK_SUBSCRIPTION_DISABLED, context)
                    .then(repository.delete(projectId, subscriptionId)));
  }

  @Transactional(transactionManager = "connectionFactoryTransactionManager")
  public Mono<String> rotateSecret(UUID projectId, UUID subscriptionId, EventContext context) {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    String plaintextSecret = WebhookSecretCrypto.generatePlaintextSecret();
    byte[] encrypted = secretCrypto.encrypt(plaintextSecret);
    return repository
        .rotateSecret(projectId, subscriptionId, encrypted, now)
        .switchIfEmpty(
            Mono.error(ControlPlaneException.notFound("Webhook subscription was not found")))
        .flatMap(
            subscription ->
                recordSubscriptionEvent(
                        subscription, PlatformEventTypes.WEBHOOK_SECRET_ROTATED, context)
                    .thenReturn(plaintextSecret))
        .doOnSuccess(
            ignored ->
                log.info(
                    "webhook_secret_rotated subscriptionId={} projectId={}",
                    subscriptionId,
                    projectId));
  }

  @Transactional(transactionManager = "connectionFactoryTransactionManager")
  public Mono<UUID> testDelivery(UUID projectId, UUID subscriptionId, EventContext context) {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    return repository
        .findById(projectId, subscriptionId)
        .switchIfEmpty(
            Mono.error(ControlPlaneException.notFound("Webhook subscription was not found")))
        .flatMap(
            subscription -> {
              RecordPlatformEventRequest eventRequest =
                  RecordPlatformEventRequest.of(
                      PlatformEventTypes.WEBHOOK_TEST,
                      projectId,
                      null,
                      "WEBHOOK_SUBSCRIPTION",
                      subscriptionId.toString(),
                      context,
                      Map.of(
                          "subscriptionId",
                          subscriptionId.toString(),
                          "test",
                          true,
                          "message",
                          "Webhook test delivery"));
              return eventRecorder
                  .record(eventRequest)
                  .flatMap(
                      eventId ->
                          deliveryRepository
                              .createPending(
                                  subscriptionId, eventId, subscription.secretVersion(), now, null)
                              .map(WebhookDeliveryEntity::id));
            });
  }

  private Mono<Void> recordSubscriptionEvent(
      WebhookSubscriptionEntity subscription, String eventType, EventContext context) {
    return eventRecorder
        .record(
            RecordPlatformEventRequest.of(
                eventType,
                subscription.projectId(),
                null,
                "WEBHOOK_SUBSCRIPTION",
                subscription.id().toString(),
                context,
                Map.of(
                    "subscriptionId", subscription.id().toString(),
                    "name", subscription.name(),
                    "enabled", subscription.enabled())))
        .then();
  }

  private void validateFilters(List<String> eventFilters) {
    if (eventFilters == null) {
      return;
    }
    if (eventFilters.size() > properties.maxEventFilters()) {
      throw ControlPlaneException.invalidRequest("Too many event filters");
    }
    for (String filter : eventFilters) {
      PlatformEventTypes.validateFilterType(filter);
    }
  }

  public record WebhookSubscriptionView(
      UUID id,
      UUID projectId,
      String name,
      String description,
      String url,
      boolean enabled,
      List<String> eventFilters,
      List<String> resourceFilters,
      int maxAttempts,
      int initialBackoffSeconds,
      int maxBackoffSeconds,
      int timeoutMs,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt,
      OffsetDateTime disabledAt) {

    static WebhookSubscriptionView from(WebhookSubscriptionEntity entity) {
      ObjectMapper mapper = new ObjectMapper();
      return new WebhookSubscriptionView(
          entity.id(),
          entity.projectId(),
          entity.name(),
          entity.description(),
          entity.url(),
          entity.enabled(),
          readList(entity.eventFilters(), mapper),
          readList(entity.resourceFilters(), mapper),
          entity.maxAttempts(),
          entity.initialBackoffSeconds(),
          entity.maxBackoffSeconds(),
          entity.timeoutMs(),
          entity.createdAt(),
          entity.updatedAt(),
          entity.disabledAt());
    }

    private static List<String> readList(String json, ObjectMapper mapper) {
      try {
        return mapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<>() {});
      } catch (Exception ex) {
        return List.of();
      }
    }
  }
}
