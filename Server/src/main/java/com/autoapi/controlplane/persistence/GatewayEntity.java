package com.autoapi.controlplane.persistence;

import io.r2dbc.postgresql.codec.Json;
import java.time.OffsetDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("gateways")
public record GatewayEntity(
    @Id String id,
    @Column("gateway_group") String gatewayGroup,
    @Column("software_version") String softwareVersion,
    @Column("started_at") OffsetDateTime startedAt,
    @Column("registered_at") OffsetDateTime registeredAt,
    @Column("last_seen_at") OffsetDateTime lastSeenAt,
    @Column("instance_address") String instanceAddress,
    Json metadata,
    @Column("created_at") OffsetDateTime createdAt,
    @Column("updated_at") OffsetDateTime updatedAt) {}
