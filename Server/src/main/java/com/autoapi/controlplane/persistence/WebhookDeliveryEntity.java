package com.autoapi.controlplane.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("webhook_deliveries")
public record WebhookDeliveryEntity(
    @Id UUID id,
    @Column("subscription_id") UUID subscriptionId,
    @Column("event_id") UUID eventId,
    String status,
    @Column("attempt_count") int attemptCount,
    @Column("next_attempt_at") OffsetDateTime nextAttemptAt,
    @Column("last_attempt_at") OffsetDateTime lastAttemptAt,
    @Column("delivered_at") OffsetDateTime deliveredAt,
    @Column("dead_lettered_at") OffsetDateTime deadLetteredAt,
    @Column("last_status_code") Integer lastStatusCode,
    @Column("last_error_type") String lastErrorType,
    @Column("last_error_summary") String lastErrorSummary,
    @Column("secret_version") int secretVersion,
    @Column("replay_of_delivery_id") UUID replayOfDeliveryId,
    @Column("created_at") OffsetDateTime createdAt,
    @Column("updated_at") OffsetDateTime updatedAt) {}
