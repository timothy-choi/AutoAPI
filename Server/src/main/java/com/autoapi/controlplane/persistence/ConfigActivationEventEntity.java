package com.autoapi.controlplane.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("config_activation_events")
public record ConfigActivationEventEntity(
    @Id UUID id,
    @Column("gateway_id") String gatewayId,
    @Column("api_id") UUID apiId,
    long version,
    @Column("content_hash") String contentHash,
    @Column("report_id") UUID reportId,
    String status,
    @Column("error_code") String errorCode,
    String diagnostic,
    @Column("apply_duration_ms") Long applyDurationMs,
    @Column("created_at") OffsetDateTime createdAt)
    implements NewEntity {}
