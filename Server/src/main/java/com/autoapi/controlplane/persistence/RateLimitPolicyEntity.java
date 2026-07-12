package com.autoapi.controlplane.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("rate_limit_policies")
public record RateLimitPolicyEntity(
    @Id UUID id,
    @Column("api_id") UUID apiId,
    String name,
    @Column("limit_count") int limitCount,
    @Column("window_seconds") int windowSeconds,
    @Column("identity_source") String identitySource,
    @Column("redis_failure_mode") String redisFailureMode,
    boolean enabled,
    @Column("created_at") OffsetDateTime createdAt,
    @Column("updated_at") OffsetDateTime updatedAt)
    implements NewEntity {}
