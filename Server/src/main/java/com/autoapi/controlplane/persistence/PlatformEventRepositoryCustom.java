package com.autoapi.controlplane.persistence;

import com.autoapi.controlplane.events.RecordPlatformEventRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
@ConditionalOnProperty(
    name = "autoapi.controlplane.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class PlatformEventRepositoryCustom {

  private final DatabaseClient databaseClient;
  private final ObjectMapper objectMapper;

  public PlatformEventRepositoryCustom(DatabaseClient databaseClient, ObjectMapper objectMapper) {
    this.databaseClient = databaseClient;
    this.objectMapper = objectMapper;
  }

  public Mono<PlatformEventEntity> insert(RecordPlatformEventRequest request) {
    UUID id = UUID.randomUUID();
    OffsetDateTime now = request.occurredAt();
    OffsetDateTime recordedAt = OffsetDateTime.now(java.time.ZoneOffset.UTC);
    String payloadJson = writeJson(request.payload());
    String metadataJson = writeJson(request.metadata());
    DatabaseClient.GenericExecuteSpec spec =
        databaseClient
            .sql(
                """
                INSERT INTO platform_events (
                  id, event_type, event_version, project_id, api_id, resource_type, resource_id,
                  actor_type, actor_id, source, correlation_id, causation_id, occurred_at, recorded_at,
                  payload, metadata, webhook_dispatch_status, created_at
                ) VALUES (
                  :id, :eventType, :eventVersion, :projectId, :apiId, :resourceType, :resourceId,
                  :actorType, :actorId, :source, :correlationId, :causationId, :occurredAt, :recordedAt,
                  :payload::jsonb, :metadata::jsonb, 'PENDING', :createdAt
                )
                RETURNING id, sequence, event_type, event_version, project_id, api_id, resource_type,
                          resource_id, actor_type, actor_id, source, correlation_id, causation_id,
                          occurred_at, recorded_at, payload::text, metadata::text,
                          webhook_dispatch_status, created_at
                """)
            .bind("id", id)
            .bind("eventType", request.eventType())
            .bind("eventVersion", request.eventVersion());
    spec = bindNullableUuid(spec, "projectId", request.projectId());
    spec = bindNullableUuid(spec, "apiId", request.apiId());
    spec =
        spec.bind("resourceType", request.resourceType())
            .bind("resourceId", request.resourceId())
            .bind("actorType", request.context().actorType())
            .bind("actorId", request.context().actorId())
            .bind("source", request.context().source())
            .bind("correlationId", request.context().correlationId());
    spec = bindNullableUuid(spec, "causationId", request.context().causationId());
    return spec.bind("occurredAt", now)
        .bind("recordedAt", recordedAt)
        .bind("payload", payloadJson)
        .bind("metadata", metadataJson)
        .bind("createdAt", recordedAt)
        .map(this::mapEvent)
        .one();
  }

  public Mono<PlatformEventEntity> findById(UUID eventId) {
    return databaseClient
        .sql(
            """
            SELECT id, sequence, event_type, event_version, project_id, api_id, resource_type,
                   resource_id, actor_type, actor_id, source, correlation_id, causation_id,
                   occurred_at, recorded_at, payload::text, metadata::text,
                   webhook_dispatch_status, created_at
            FROM platform_events WHERE id = :id
            """)
        .bind("id", eventId)
        .map(this::mapEvent)
        .one();
  }

  public Flux<PlatformEventEntity> list(
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
    StringBuilder sql =
        new StringBuilder(
            """
            SELECT id, sequence, event_type, event_version, project_id, api_id, resource_type,
                   resource_id, actor_type, actor_id, source, correlation_id, causation_id,
                   occurred_at, recorded_at, payload::text, metadata::text,
                   webhook_dispatch_status, created_at
            FROM platform_events WHERE 1=1
            """);
    List<String> clauses = new ArrayList<>();
    if (projectId != null) {
      clauses.add("project_id = :projectId");
    }
    if (apiId != null) {
      clauses.add("api_id = :apiId");
    }
    if (eventType != null && !eventType.isBlank()) {
      clauses.add("event_type = :eventType");
    }
    if (resourceType != null && !resourceType.isBlank()) {
      clauses.add("resource_type = :resourceType");
    }
    if (resourceId != null && !resourceId.isBlank()) {
      clauses.add("resource_id = :resourceId");
    }
    if (correlationId != null && !correlationId.isBlank()) {
      clauses.add("correlation_id = :correlationId");
    }
    if (afterSequence != null) {
      clauses.add("sequence > :afterSequence");
    }
    if (occurredAfter != null) {
      clauses.add("occurred_at >= :occurredAfter");
    }
    if (occurredBefore != null) {
      clauses.add("occurred_at <= :occurredBefore");
    }
    for (String clause : clauses) {
      sql.append(" AND ").append(clause);
    }
    sql.append(" ORDER BY sequence ASC LIMIT :limit");

    DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql.toString());
    if (projectId != null) {
      spec = spec.bind("projectId", projectId);
    }
    if (apiId != null) {
      spec = spec.bind("apiId", apiId);
    }
    if (eventType != null && !eventType.isBlank()) {
      spec = spec.bind("eventType", eventType);
    }
    if (resourceType != null && !resourceType.isBlank()) {
      spec = spec.bind("resourceType", resourceType);
    }
    if (resourceId != null && !resourceId.isBlank()) {
      spec = spec.bind("resourceId", resourceId);
    }
    if (correlationId != null && !correlationId.isBlank()) {
      spec = spec.bind("correlationId", correlationId);
    }
    if (afterSequence != null) {
      spec = spec.bind("afterSequence", afterSequence);
    }
    if (occurredAfter != null) {
      spec = spec.bind("occurredAfter", occurredAfter);
    }
    if (occurredBefore != null) {
      spec = spec.bind("occurredBefore", occurredBefore);
    }
    return spec.bind("limit", limit).map(this::mapEvent).all();
  }

  public Flux<PlatformEventEntity> claimPendingDispatch(int batchSize) {
    return databaseClient
        .sql(
            """
            UPDATE platform_events
            SET webhook_dispatch_status = 'DISPATCHED'
            WHERE id IN (
              SELECT id FROM platform_events
              WHERE webhook_dispatch_status = 'PENDING'
              ORDER BY sequence
              LIMIT :batchSize
              FOR UPDATE SKIP LOCKED
            )
            RETURNING id, sequence, event_type, event_version, project_id, api_id, resource_type,
                      resource_id, actor_type, actor_id, source, correlation_id, causation_id,
                      occurred_at, recorded_at, payload::text, metadata::text,
                      webhook_dispatch_status, created_at
            """)
        .bind("batchSize", batchSize)
        .map(this::mapEvent)
        .all();
  }

  public Mono<Void> markDispatchSkipped(UUID eventId) {
    return databaseClient
        .sql(
            """
            UPDATE platform_events
            SET webhook_dispatch_status = 'SKIPPED'
            WHERE id = :id AND webhook_dispatch_status = 'DISPATCHED'
            """)
        .bind("id", eventId)
        .fetch()
        .rowsUpdated()
        .then();
  }

  private static DatabaseClient.GenericExecuteSpec bindNullableUuid(
      DatabaseClient.GenericExecuteSpec spec, String name, UUID value) {
    return value == null ? spec.bindNull(name, UUID.class) : spec.bind(name, value);
  }

  private String writeJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value == null ? java.util.Map.of() : value);
    } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
      throw new IllegalArgumentException("Failed to serialize JSON", ex);
    }
  }

  private PlatformEventEntity mapEvent(
      io.r2dbc.spi.Readable row, io.r2dbc.spi.RowMetadata metadata) {
    return new PlatformEventEntity(
        row.get("id", UUID.class),
        row.get("sequence", Long.class),
        row.get("event_type", String.class),
        row.get("event_version", Integer.class),
        row.get("project_id", UUID.class),
        row.get("api_id", UUID.class),
        row.get("resource_type", String.class),
        row.get("resource_id", String.class),
        row.get("actor_type", String.class),
        row.get("actor_id", String.class),
        row.get("source", String.class),
        row.get("correlation_id", String.class),
        row.get("causation_id", UUID.class),
        row.get("occurred_at", OffsetDateTime.class),
        row.get("recorded_at", OffsetDateTime.class),
        row.get("payload", String.class),
        row.get("metadata", String.class),
        row.get("webhook_dispatch_status", String.class),
        row.get("created_at", OffsetDateTime.class));
  }
}
