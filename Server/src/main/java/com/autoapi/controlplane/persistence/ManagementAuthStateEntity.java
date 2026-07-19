package com.autoapi.controlplane.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("management_auth_state")
public record ManagementAuthStateEntity(
    @Id Integer id,
    Boolean initialized,
    @Column("bootstrap_organization_id") UUID bootstrapOrganizationId,
    @Column("initialized_at") OffsetDateTime initializedAt) {}
