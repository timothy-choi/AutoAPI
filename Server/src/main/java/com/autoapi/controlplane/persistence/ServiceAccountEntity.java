package com.autoapi.controlplane.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("service_accounts")
public record ServiceAccountEntity(
    @Id UUID id,
    @Column("organization_id") UUID organizationId,
    @Column("project_id") UUID projectId,
    String name,
    String description,
    String status,
    @Column("created_at") OffsetDateTime createdAt,
    @Column("updated_at") OffsetDateTime updatedAt,
    @Column("disabled_at") OffsetDateTime disabledAt)
    implements NewEntity {}
