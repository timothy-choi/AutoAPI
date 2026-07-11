package com.autoapi.controlplane.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("upstream_targets")
public record UpstreamTargetEntity(
    @Id UUID id,
    @Column("upstream_pool_id") UUID upstreamPoolId,
    String url,
    boolean enabled,
    int weight,
    @Column("created_at") OffsetDateTime createdAt,
    @Column("updated_at") OffsetDateTime updatedAt)
    implements NewEntity {}
