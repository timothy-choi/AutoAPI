package com.autoapi.controlplane.persistence;

import io.r2dbc.postgresql.codec.Json;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("policy_overrides")
public record PolicyOverrideEntity(
    @Id UUID id,
    @Column("scope_level") String scopeLevel,
    @Column("organization_id") UUID organizationId,
    @Column("project_id") UUID projectId,
    @Column("gateway_group_id") UUID gatewayGroupId,
    @Column("api_id") UUID apiId,
    @Column("route_id") UUID routeId,
    @Column("policy_type") String policyType,
    String mode,
    @Column("content_json") Json contentJson,
    @Column("created_at") OffsetDateTime createdAt,
    @Column("updated_at") OffsetDateTime updatedAt)
    implements NewEntity {}
