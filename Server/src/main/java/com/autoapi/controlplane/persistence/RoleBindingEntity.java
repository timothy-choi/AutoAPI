package com.autoapi.controlplane.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("role_bindings")
public record RoleBindingEntity(
    @Id UUID id,
    @Column("organization_id") UUID organizationId,
    @Column("project_id") UUID projectId,
    @Column("principal_type") String principalType,
    @Column("principal_id") UUID principalId,
    String role,
    @Column("created_by_principal_type") String createdByPrincipalType,
    @Column("created_by_principal_id") UUID createdByPrincipalId,
    @Column("created_at") OffsetDateTime createdAt,
    @Column("expires_at") OffsetDateTime expiresAt,
    @Column("revoked_at") OffsetDateTime revokedAt)
    implements NewEntity {}
