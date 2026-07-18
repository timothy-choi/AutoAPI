package com.autoapi.controlplane.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("route_policy_bindings")
public record RoutePolicyBindingEntity(
    @Id @Column("route_id") UUID routeId,
    @Column("authentication_required") boolean authenticationRequired,
    @Column("rate_limit_policy_id") UUID rateLimitPolicyId,
    @Column("created_at") OffsetDateTime createdAt,
    @Column("updated_at") OffsetDateTime updatedAt,
    @Column("retry_policy_id") UUID retryPolicyId,
    @Column("traffic_split_policy_id") UUID trafficSplitPolicyId,
    @Column("circuit_breaker_policy_id") UUID circuitBreakerPolicyId)
    implements NewEntity {

  @Override
  public UUID id() {
    return routeId;
  }
}
