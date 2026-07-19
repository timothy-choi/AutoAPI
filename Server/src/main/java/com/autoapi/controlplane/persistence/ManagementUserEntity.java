package com.autoapi.controlplane.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("management_users")
public record ManagementUserEntity(
    @Id UUID id,
    @Column("organization_id") UUID organizationId,
    @Column("external_subject") String externalSubject,
    String email,
    @Column("display_name") String displayName,
    String status,
    @Column("created_at") OffsetDateTime createdAt,
    @Column("updated_at") OffsetDateTime updatedAt,
    @Column("last_authenticated_at") OffsetDateTime lastAuthenticatedAt)
    implements NewEntity {}
