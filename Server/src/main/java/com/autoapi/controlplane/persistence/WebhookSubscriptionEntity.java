package com.autoapi.controlplane.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("webhook_subscriptions")
public record WebhookSubscriptionEntity(
    @Id UUID id,
    @Column("project_id") UUID projectId,
    String name,
    String description,
    String url,
    boolean enabled,
    @Column("event_filters") String eventFilters,
    @Column("resource_filters") String resourceFilters,
    @Column("encrypted_secret") byte[] encryptedSecret,
    @Column("secret_version") int secretVersion,
    @Column("max_attempts") int maxAttempts,
    @Column("initial_backoff_seconds") int initialBackoffSeconds,
    @Column("max_backoff_seconds") int maxBackoffSeconds,
    @Column("timeout_ms") int timeoutMs,
    @Column("created_at") OffsetDateTime createdAt,
    @Column("updated_at") OffsetDateTime updatedAt,
    @Column("disabled_at") OffsetDateTime disabledAt) {}
