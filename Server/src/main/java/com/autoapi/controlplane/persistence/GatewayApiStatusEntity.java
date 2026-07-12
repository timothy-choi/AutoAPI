package com.autoapi.controlplane.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("gateway_api_status")
public record GatewayApiStatusEntity(
    @Column("gateway_id") String gatewayId,
    @Column("api_id") UUID apiId,
    @Column("active_version") Long activeVersion,
    @Column("active_content_hash") String activeContentHash,
    @Column("last_attempted_version") Long lastAttemptedVersion,
    @Column("last_attempted_content_hash") String lastAttemptedContentHash,
    @Column("last_status") String lastStatus,
    @Column("last_error_code") String lastErrorCode,
    @Column("last_diagnostic") String lastDiagnostic,
    @Column("last_apply_duration_ms") Long lastApplyDurationMs,
    @Column("last_reported_at") OffsetDateTime lastReportedAt,
    @Column("created_at") OffsetDateTime createdAt,
    @Column("updated_at") OffsetDateTime updatedAt) {}
