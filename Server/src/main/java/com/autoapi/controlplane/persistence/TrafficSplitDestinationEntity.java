package com.autoapi.controlplane.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("traffic_split_destinations")
public record TrafficSplitDestinationEntity(
    @Id UUID id,
    @Column("traffic_split_policy_id") UUID trafficSplitPolicyId,
    @Column("upstream_pool_id") UUID upstreamPoolId,
    String name,
    int weight,
    int priority,
    @Column("is_primary") boolean primary,
    @Column("created_at") OffsetDateTime createdAt,
    @Column("updated_at") OffsetDateTime updatedAt)
    implements NewEntity {}
