package com.autoapi.controlplane.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("routes")
public record RouteEntity(
    @Id UUID id,
    @Column("api_id") UUID apiId,
    String name,
    String host,
    @Column("path_prefix") String pathPrefix,
    String[] methods,
    @Column("upstream_pool_id") UUID upstreamPoolId,
    boolean enabled,
    @Column("created_at") OffsetDateTime createdAt,
    @Column("updated_at") OffsetDateTime updatedAt) {}
