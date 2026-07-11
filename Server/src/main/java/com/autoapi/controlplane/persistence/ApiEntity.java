package com.autoapi.controlplane.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("apis")
public record ApiEntity(
    @Id UUID id,
    @Column("project_id") UUID projectId,
    String name,
    String host,
    @Column("base_path") String basePath,
    boolean enabled,
    @Column("desired_config_version") Long desiredConfigVersion,
    @Column("created_at") OffsetDateTime createdAt,
    @Column("updated_at") OffsetDateTime updatedAt) {}
