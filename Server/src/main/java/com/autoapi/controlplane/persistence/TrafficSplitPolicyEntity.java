package com.autoapi.controlplane.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("traffic_split_policies")
public record TrafficSplitPolicyEntity(
    @Id UUID id,
    @Column("api_id") UUID apiId,
    String name,
    @Column("selection_key") String selectionKey,
    @Column("selection_key_name") String selectionKeyName,
    @Column("fallback_mode") String fallbackMode,
    boolean enabled,
    @Column("created_at") OffsetDateTime createdAt,
    @Column("updated_at") OffsetDateTime updatedAt)
    implements NewEntity {}
