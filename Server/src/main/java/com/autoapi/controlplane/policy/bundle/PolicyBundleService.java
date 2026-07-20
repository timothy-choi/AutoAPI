package com.autoapi.controlplane.policy.bundle;

import com.autoapi.controlplane.api.ControlPlaneException;
import com.autoapi.controlplane.events.EventContext;
import com.autoapi.controlplane.events.PlatformEventRecorder;
import com.autoapi.controlplane.events.PlatformEventTypes;
import com.autoapi.controlplane.events.RecordPlatformEventRequest;
import com.autoapi.controlplane.managementauth.OrganizationService;
import com.autoapi.controlplane.persistence.PolicyBundleEntity;
import com.autoapi.controlplane.persistence.PolicyBundleRepositoryCustom;
import com.autoapi.controlplane.persistence.PolicyBundleRevisionEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@ConditionalOnProperty(
    name = "autoapi.controlplane.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class PolicyBundleService {

  private final PolicyBundleRepositoryCustom repository;
  private final OrganizationService organizationService;
  private final PlatformEventRecorder eventRecorder;
  private final PolicyCacheInvalidator cacheInvalidator;
  private final PolicyAuditService auditService;
  private final ObjectMapper objectMapper;

  public PolicyBundleService(
      PolicyBundleRepositoryCustom repository,
      OrganizationService organizationService,
      PlatformEventRecorder eventRecorder,
      PolicyCacheInvalidator cacheInvalidator,
      PolicyAuditService auditService,
      ObjectMapper objectMapper) {
    this.repository = repository;
    this.organizationService = organizationService;
    this.eventRecorder = eventRecorder;
    this.cacheInvalidator = cacheInvalidator;
    this.auditService = auditService;
    this.objectMapper = objectMapper;
  }

  @Transactional(transactionManager = "connectionFactoryTransactionManager")
  public Mono<PolicyBundleView> create(
      UUID organizationId, String name, String description, Boolean enabled, EventContext context) {
    if (name == null || name.isBlank()) {
      return Mono.error(ControlPlaneException.invalidRequest("name is required"));
    }
    OffsetDateTime now = now();
    return organizationService
        .get(organizationId)
        .then(
            repository.insert(
                organizationId, name.trim(), description, enabled == null || enabled, now))
        .flatMap(
            bundle ->
                recordEvent(bundle, PlatformEventTypes.POLICY_BUNDLE_CREATED, context)
                    .then(auditService.recordBundleAction(context, "BUNDLE_CREATED", bundle, null))
                    .then(cacheInvalidator.invalidate())
                    .thenReturn(PolicyBundleView.from(bundle)))
        .onErrorResume(
            DataIntegrityViolationException.class,
            ex -> Mono.error(ControlPlaneException.conflict("Policy bundle name already exists")));
  }

  public Flux<PolicyBundleView> list(UUID organizationId, int limit, int offset) {
    return organizationService
        .get(organizationId)
        .thenMany(repository.listByOrganization(organizationId, limit, offset))
        .map(PolicyBundleView::from);
  }

  public Mono<PolicyBundleView> get(UUID organizationId, UUID bundleId) {
    return organizationService
        .get(organizationId)
        .then(repository.findByOrganizationAndId(organizationId, bundleId))
        .switchIfEmpty(Mono.error(ControlPlaneException.notFound("Policy bundle was not found")))
        .map(PolicyBundleView::from);
  }

  @Transactional(transactionManager = "connectionFactoryTransactionManager")
  public Mono<PolicyBundleView> update(
      UUID organizationId,
      UUID bundleId,
      String description,
      Boolean enabled,
      EventContext context) {
    OffsetDateTime now = now();
    return get(organizationId, bundleId)
        .then(repository.update(organizationId, bundleId, description, enabled, now))
        .switchIfEmpty(Mono.error(ControlPlaneException.notFound("Policy bundle was not found")))
        .flatMap(
            bundle ->
                recordEvent(bundle, PlatformEventTypes.POLICY_BUNDLE_UPDATED, context)
                    .then(auditService.recordBundleAction(context, "BUNDLE_UPDATED", bundle, null))
                    .then(cacheInvalidator.invalidate())
                    .thenReturn(PolicyBundleView.from(bundle)));
  }

  @Transactional(transactionManager = "connectionFactoryTransactionManager")
  public Mono<PolicyBundleRevisionView> createRevision(
      UUID organizationId, UUID bundleId, JsonNode content, String message, EventContext context) {
    if (content == null || !content.isObject()) {
      return Mono.error(ControlPlaneException.invalidRequest("content must be a JSON object"));
    }
    OffsetDateTime now = now();
    return get(organizationId, bundleId)
        .then(repository.nextRevisionNumber(bundleId))
        .flatMap(
            revisionNumber -> {
              String contentJson;
              try {
                contentJson = objectMapper.writeValueAsString(content);
              } catch (Exception ex) {
                return Mono.error(
                    ControlPlaneException.invalidRequest("content is not valid JSON"));
              }
              return repository.insertRevision(
                  bundleId,
                  revisionNumber,
                  contentJson,
                  message,
                  now,
                  context == null ? null : context.actorType(),
                  parseActorId(context));
            })
        .flatMap(
            revision ->
                recordRevisionEvent(bundleId, organizationId, revision, context)
                    .then(
                        auditService.recordRevisionAction(
                            context, "REVISION_CREATED", bundleId, revision))
                    .then(cacheInvalidator.invalidate())
                    .thenReturn(PolicyBundleRevisionView.from(revision)));
  }

  public Flux<PolicyBundleRevisionView> listRevisions(
      UUID organizationId, UUID bundleId, int limit, int offset) {
    return get(organizationId, bundleId)
        .thenMany(repository.listRevisions(bundleId, limit, offset))
        .map(PolicyBundleRevisionView::from);
  }

  public Mono<PolicyBundleEntity> requireBundle(UUID organizationId, UUID bundleId) {
    return repository
        .findByOrganizationAndId(organizationId, bundleId)
        .switchIfEmpty(Mono.error(ControlPlaneException.notFound("Policy bundle was not found")));
  }

  public Mono<PolicyBundleEntity> requireBundle(UUID bundleId) {
    return repository
        .findById(bundleId)
        .switchIfEmpty(Mono.error(ControlPlaneException.notFound("Policy bundle was not found")));
  }

  private Mono<Void> recordEvent(
      PolicyBundleEntity bundle, String eventType, EventContext context) {
    Map<String, Object> payload = new HashMap<>();
    payload.put("bundleId", bundle.id().toString());
    payload.put("organizationId", bundle.organizationId().toString());
    payload.put("name", bundle.name());
    return eventRecorder
        .record(
            RecordPlatformEventRequest.of(
                eventType, null, null, "POLICY_BUNDLE", bundle.id().toString(), context, payload))
        .then();
  }

  private Mono<Void> recordRevisionEvent(
      UUID bundleId,
      UUID organizationId,
      PolicyBundleRevisionEntity revision,
      EventContext context) {
    Map<String, Object> payload = new HashMap<>();
    payload.put("bundleId", bundleId.toString());
    payload.put("organizationId", organizationId.toString());
    payload.put("revisionNumber", revision.revisionNumber());
    return eventRecorder
        .record(
            RecordPlatformEventRequest.of(
                PlatformEventTypes.POLICY_BUNDLE_REVISION_CREATED,
                null,
                null,
                "POLICY_BUNDLE_REVISION",
                revision.id().toString(),
                context,
                payload))
        .then();
  }

  private static UUID parseActorId(EventContext context) {
    if (context == null || context.actorId() == null) {
      return null;
    }
    try {
      return UUID.fromString(context.actorId());
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }

  private static OffsetDateTime now() {
    return OffsetDateTime.now(ZoneOffset.UTC);
  }

  public record PolicyBundleView(
      UUID id,
      UUID organizationId,
      String name,
      String description,
      boolean enabled,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt) {

    static PolicyBundleView from(PolicyBundleEntity entity) {
      return new PolicyBundleView(
          entity.id(),
          entity.organizationId(),
          entity.name(),
          entity.description(),
          entity.enabled(),
          entity.createdAt(),
          entity.updatedAt());
    }
  }

  public record PolicyBundleRevisionView(
      UUID id,
      UUID bundleId,
      int revisionNumber,
      String contentJson,
      String message,
      OffsetDateTime createdAt,
      String createdByPrincipalType,
      UUID createdByPrincipalId) {

    static PolicyBundleRevisionView from(PolicyBundleRevisionEntity entity) {
      return new PolicyBundleRevisionView(
          entity.id(),
          entity.bundleId(),
          entity.revisionNumber(),
          entity.contentJson().asString(),
          entity.message(),
          entity.createdAt(),
          entity.createdByPrincipalType(),
          entity.createdByPrincipalId());
    }
  }
}
