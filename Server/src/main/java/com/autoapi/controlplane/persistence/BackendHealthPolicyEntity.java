package com.autoapi.controlplane.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("backend_health_policies")
public record BackendHealthPolicyEntity(
    @Id UUID id,
    @Column("api_id") UUID apiId,
    String name,
    @Column("consecutive_failure_threshold") int consecutiveFailureThreshold,
    @Column("ejection_duration_seconds") int ejectionDurationSeconds,
    @Column("max_ejection_percent") int maxEjectionPercent,
    boolean enabled,
    @Column("created_at") OffsetDateTime createdAt,
    @Column("updated_at") OffsetDateTime updatedAt)
    implements NewEntity {}
