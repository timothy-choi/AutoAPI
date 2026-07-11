package com.autoapi.controlplane.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("config_versions")
public record ConfigVersionEntity(
    @Id UUID id,
    @Column("api_id") UUID apiId,
    long version,
    @Column("content_hash") String contentHash,
    @Column("config_snapshot") String configSnapshot,
    String message,
    @Column("created_at") OffsetDateTime createdAt) {}
