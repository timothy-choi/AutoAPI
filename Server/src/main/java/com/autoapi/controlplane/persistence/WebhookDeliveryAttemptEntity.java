package com.autoapi.controlplane.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("webhook_delivery_attempts")
public record WebhookDeliveryAttemptEntity(
    @Id UUID id,
    @Column("delivery_id") UUID deliveryId,
    @Column("attempt_number") int attemptNumber,
    @Column("started_at") OffsetDateTime startedAt,
    @Column("completed_at") OffsetDateTime completedAt,
    @Column("duration_ms") Integer durationMs,
    @Column("status_code") Integer statusCode,
    String result,
    @Column("error_type") String errorType,
    @Column("response_body_preview") String responseBodyPreview,
    @Column("created_at") OffsetDateTime createdAt) {}
