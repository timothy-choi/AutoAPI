package com.autoapi.controlplane.persistence;

import java.time.OffsetDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("global_policy_cache_generation")
public record GlobalPolicyCacheGenerationEntity(
    @Id int id, long generation, @Column("updated_at") OffsetDateTime updatedAt) {}
