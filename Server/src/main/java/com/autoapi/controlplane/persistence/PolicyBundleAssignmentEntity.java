package com.autoapi.controlplane.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("policy_bundle_assignments")
public record PolicyBundleAssignmentEntity(
    @Id UUID id,
    @Column("bundle_id") UUID bundleId,
    @Column("revision_number") int revisionNumber,
    @Column("scope_level") String scopeLevel,
    @Column("organization_id") UUID organizationId,
    @Column("project_id") UUID projectId,
    @Column("gateway_group_id") UUID gatewayGroupId,
    @Column("api_id") UUID apiId,
    @Column("route_id") UUID routeId,
    boolean enabled,
    @Column("created_at") OffsetDateTime createdAt,
    @Column("updated_at") OffsetDateTime updatedAt)
    implements NewEntity {}
