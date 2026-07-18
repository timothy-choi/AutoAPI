package com.autoapi.controlplane.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("service_instances")
public record ServiceInstanceEntity(
    @Id UUID id,
    @Column("service_id") UUID serviceId,
    @Column("instance_id") String instanceId,
    String host,
    int port,
    String scheme,
    String zone,
    String region,
    int weight,
    String status,
    @Column("registration_epoch") long registrationEpoch,
    @Column("registered_at") OffsetDateTime registeredAt,
    @Column("last_heartbeat_at") OffsetDateTime lastHeartbeatAt,
    @Column("lease_expires_at") OffsetDateTime leaseExpiresAt,
    @Column("deregistered_at") OffsetDateTime deregisteredAt,
    String metadata,
    @Column("created_at") OffsetDateTime createdAt,
    @Column("updated_at") OffsetDateTime updatedAt)
    implements NewEntity {}
