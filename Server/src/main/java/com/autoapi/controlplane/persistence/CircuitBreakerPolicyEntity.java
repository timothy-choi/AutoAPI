package com.autoapi.controlplane.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("circuit_breaker_policies")
public record CircuitBreakerPolicyEntity(
    @Id UUID id,
    @Column("api_id") UUID apiId,
    String name,
    @Column("failure_threshold") int failureThreshold,
    @Column("rolling_window_seconds") int rollingWindowSeconds,
    @Column("open_duration_seconds") int openDurationSeconds,
    @Column("half_open_max_requests") int halfOpenMaxRequests,
    @Column("success_threshold") int successThreshold,
    @Column("predicate_count_http_5xx") boolean predicateCountHttp5xx,
    @Column("predicate_count_connect_failure") boolean predicateCountConnectFailure,
    @Column("predicate_count_connect_timeout") boolean predicateCountConnectTimeout,
    @Column("predicate_count_read_timeout") boolean predicateCountReadTimeout,
    @Column("predicate_count_tls_failure") boolean predicateCountTlsFailure,
    @Column("predicate_count_transport_exception") boolean predicateCountTransportException,
    @Column("predicate_count_http_429") boolean predicateCountHttp429,
    boolean enabled,
    @Column("created_at") OffsetDateTime createdAt,
    @Column("updated_at") OffsetDateTime updatedAt)
    implements NewEntity {}
