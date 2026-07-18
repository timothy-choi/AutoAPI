package com.autoapi.controlplane.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("platform_events")
public record PlatformEventEntity(
    @Id UUID id,
    long sequence,
    @Column("event_type") String eventType,
    @Column("event_version") int eventVersion,
    @Column("project_id") UUID projectId,
    @Column("api_id") UUID apiId,
    @Column("resource_type") String resourceType,
    @Column("resource_id") String resourceId,
    @Column("actor_type") String actorType,
    @Column("actor_id") String actorId,
    String source,
    @Column("correlation_id") String correlationId,
    @Column("causation_id") UUID causationId,
    @Column("occurred_at") OffsetDateTime occurredAt,
    @Column("recorded_at") OffsetDateTime recordedAt,
    String payload,
    String metadata,
    @Column("webhook_dispatch_status") String webhookDispatchStatus,
    @Column("created_at") OffsetDateTime createdAt) {}
