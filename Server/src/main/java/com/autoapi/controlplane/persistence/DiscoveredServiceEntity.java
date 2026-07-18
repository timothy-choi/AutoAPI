package com.autoapi.controlplane.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("discovered_services")
public record DiscoveredServiceEntity(
    @Id UUID id,
    @Column("project_id") UUID projectId,
    String name,
    String description,
    boolean enabled,
    @Column("selection_strategy") String selectionStrategy,
    @Column("registration_mode") String registrationMode,
    @Column("default_scheme") String defaultScheme,
    @Column("default_port") int defaultPort,
    @Column("consistent_hash_key") String consistentHashKey,
    @Column("consistent_hash_key_name") String consistentHashKeyName,
    @Column("membership_version") long membershipVersion,
    String metadata,
    @Column("created_at") OffsetDateTime createdAt,
    @Column("updated_at") OffsetDateTime updatedAt)
    implements NewEntity {}
