package com.autoapi.controlplane.events;

import com.autoapi.controlplane.api.ControlPlaneException;
import com.autoapi.controlplane.persistence.PlatformEventEntity;
import com.autoapi.controlplane.persistence.PlatformEventRepositoryCustom;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@ConditionalOnProperty(
    name = {"autoapi.controlplane.enabled", "autoapi.events.enabled"},
    havingValue = "true",
    matchIfMissing = true)
public class PlatformEventQueryService {

  private static final int MAX_PAGE_SIZE = 200;

  private final PlatformEventRepositoryCustom repository;
  private final ObjectMapper objectMapper;

  public PlatformEventQueryService(
      PlatformEventRepositoryCustom repository, ObjectMapper objectMapper) {
    this.repository = repository;
    this.objectMapper = objectMapper;
  }

  public Flux<PlatformEventView> list(
      UUID projectId,
      UUID apiId,
      String eventType,
      String resourceType,
      String resourceId,
      String correlationId,
      Long afterSequence,
      OffsetDateTime occurredAfter,
      OffsetDateTime occurredBefore,
      int limit) {
    int boundedLimit = Math.min(Math.max(limit, 1), MAX_PAGE_SIZE);
    return repository
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
            boundedLimit)
        .map(entity -> PlatformEventView.from(entity, objectMapper));
  }

  public Mono<PlatformEventView> get(UUID eventId) {
    return repository
        .findById(eventId)
        .map(entity -> PlatformEventView.from(entity, objectMapper))
        .switchIfEmpty(Mono.error(ControlPlaneException.notFound("Event was not found")));
  }

  public Flux<AuditEntryView> listAudit(UUID projectId, Long afterSequence, int limit) {
    return list(projectId, null, null, null, null, null, afterSequence, null, null, limit)
        .map(AuditEntryView::from);
  }

  public record PlatformEventView(
      UUID id,
      long sequence,
      String eventType,
      int eventVersion,
      UUID projectId,
      UUID apiId,
      String resourceType,
      String resourceId,
      String actorType,
      String actorId,
      String source,
      String correlationId,
      UUID causationId,
      OffsetDateTime occurredAt,
      OffsetDateTime recordedAt,
      Map<String, Object> payload,
      Map<String, String> metadata) {

    static PlatformEventView from(PlatformEventEntity entity, ObjectMapper objectMapper) {
      return new PlatformEventView(
          entity.id(),
          entity.sequence(),
          entity.eventType(),
          entity.eventVersion(),
          entity.projectId(),
          entity.apiId(),
          entity.resourceType(),
          entity.resourceId(),
          entity.actorType(),
          entity.actorId(),
          entity.source(),
          entity.correlationId(),
          entity.causationId(),
          entity.occurredAt(),
          entity.recordedAt(),
          readMap(entity.payload(), objectMapper),
          readMetadata(entity.metadata(), objectMapper));
    }

    private static Map<String, Object> readMap(String json, ObjectMapper objectMapper) {
      try {
        return objectMapper.readValue(json, new TypeReference<>() {});
      } catch (Exception ex) {
        return Map.of();
      }
    }

    private static Map<String, String> readMetadata(String json, ObjectMapper objectMapper) {
      try {
        return objectMapper.readValue(json, new TypeReference<>() {});
      } catch (Exception ex) {
        return Map.of();
      }
    }
  }

  public record AuditEntryView(
      UUID id,
      long sequence,
      String action,
      String actor,
      String resource,
      String summary,
      OffsetDateTime timestamp,
      String correlationId) {

    static AuditEntryView from(PlatformEventView event) {
      return new AuditEntryView(
          event.id(),
          event.sequence(),
          event.eventType(),
          event.actorType() + ":" + event.actorId(),
          event.resourceType() + ":" + event.resourceId(),
          event.eventType(),
          event.occurredAt(),
          event.correlationId());
    }
  }
}
