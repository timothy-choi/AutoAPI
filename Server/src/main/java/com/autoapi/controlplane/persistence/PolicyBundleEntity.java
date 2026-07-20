package com.autoapi.controlplane.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("policy_bundles")
public record PolicyBundleEntity(
    @Id UUID id,
    @Column("organization_id") UUID organizationId,
    String name,
    String description,
    boolean enabled,
    @Column("created_at") OffsetDateTime createdAt,
    @Column("updated_at") OffsetDateTime updatedAt)
    implements NewEntity {}
