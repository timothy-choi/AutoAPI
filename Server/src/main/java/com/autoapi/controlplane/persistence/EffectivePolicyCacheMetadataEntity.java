package com.autoapi.controlplane.persistence;

import java.time.OffsetDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("effective_policy_cache_metadata")
public record EffectivePolicyCacheMetadataEntity(
    @Id @Column("scope_key") String scopeKey,
    @Column("cache_generation") long cacheGeneration,
    @Column("content_hash") String contentHash,
    @Column("updated_at") OffsetDateTime updatedAt) {}
