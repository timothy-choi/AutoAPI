package com.autoapi.controlplane.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("organizations")
public record OrganizationEntity(
    @Id UUID id,
    String name,
    String slug,
    String status,
    @Column("created_at") OffsetDateTime createdAt,
    @Column("updated_at") OffsetDateTime updatedAt,
    @Column("deleted_at") OffsetDateTime deletedAt)
    implements NewEntity {}
