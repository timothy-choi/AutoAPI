package com.autoapi.controlplane.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
@ConditionalOnProperty(
    name = "autoapi.controlplane.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class CircuitBreakerPolicyRepositoryCustom {

  private final DatabaseClient databaseClient;

  public CircuitBreakerPolicyRepositoryCustom(DatabaseClient databaseClient) {
    this.databaseClient = databaseClient;
  }

  public Mono<CircuitBreakerPolicyEntity> patch(
      UUID id,
      UUID apiId,
      Integer failureThreshold,
      Integer rollingWindowSeconds,
      Integer openDurationSeconds,
      Integer halfOpenMaxRequests,
      Integer successThreshold,
      Boolean predicateCountHttp5xx,
      Boolean predicateCountConnectFailure,
      Boolean predicateCountConnectTimeout,
      Boolean predicateCountReadTimeout,
      Boolean predicateCountTlsFailure,
      Boolean predicateCountTransportException,
      Boolean predicateCountHttp429,
      Boolean enabled,
      OffsetDateTime updatedAt) {
    return databaseClient
        .sql(
            """
            UPDATE circuit_breaker_policies
            SET failure_threshold = COALESCE(:failureThreshold, failure_threshold),
                rolling_window_seconds = COALESCE(:rollingWindowSeconds, rolling_window_seconds),
                open_duration_seconds = COALESCE(:openDurationSeconds, open_duration_seconds),
                half_open_max_requests = COALESCE(:halfOpenMaxRequests, half_open_max_requests),
                success_threshold = COALESCE(:successThreshold, success_threshold),
                predicate_count_http_5xx = COALESCE(:predicateCountHttp5xx, predicate_count_http_5xx),
                predicate_count_connect_failure = COALESCE(:predicateCountConnectFailure, predicate_count_connect_failure),
                predicate_count_connect_timeout = COALESCE(:predicateCountConnectTimeout, predicate_count_connect_timeout),
                predicate_count_read_timeout = COALESCE(:predicateCountReadTimeout, predicate_count_read_timeout),
                predicate_count_tls_failure = COALESCE(:predicateCountTlsFailure, predicate_count_tls_failure),
                predicate_count_transport_exception = COALESCE(:predicateCountTransportException, predicate_count_transport_exception),
                predicate_count_http_429 = COALESCE(:predicateCountHttp429, predicate_count_http_429),
                enabled = COALESCE(:enabled, enabled),
                updated_at = :updatedAt
            WHERE id = :id AND api_id = :apiId
            RETURNING *
            """)
        .bind("id", id)
        .bind("apiId", apiId)
        .bind("updatedAt", updatedAt)
        .bind("failureThreshold", failureThreshold)
        .bind("rollingWindowSeconds", rollingWindowSeconds)
        .bind("openDurationSeconds", openDurationSeconds)
        .bind("halfOpenMaxRequests", halfOpenMaxRequests)
        .bind("successThreshold", successThreshold)
        .bind("predicateCountHttp5xx", predicateCountHttp5xx)
        .bind("predicateCountConnectFailure", predicateCountConnectFailure)
        .bind("predicateCountConnectTimeout", predicateCountConnectTimeout)
        .bind("predicateCountReadTimeout", predicateCountReadTimeout)
        .bind("predicateCountTlsFailure", predicateCountTlsFailure)
        .bind("predicateCountTransportException", predicateCountTransportException)
        .bind("predicateCountHttp429", predicateCountHttp429)
        .bind("enabled", enabled)
        .map(this::mapRow)
        .one();
  }

  private CircuitBreakerPolicyEntity mapRow(
      io.r2dbc.spi.Readable row, io.r2dbc.spi.RowMetadata metadata) {
    return new CircuitBreakerPolicyEntity(
        row.get("id", UUID.class),
        row.get("api_id", UUID.class),
        row.get("name", String.class),
        row.get("failure_threshold", Integer.class),
        row.get("rolling_window_seconds", Integer.class),
        row.get("open_duration_seconds", Integer.class),
        row.get("half_open_max_requests", Integer.class),
        row.get("success_threshold", Integer.class),
        Boolean.TRUE.equals(row.get("predicate_count_http_5xx", Boolean.class)),
        Boolean.TRUE.equals(row.get("predicate_count_connect_failure", Boolean.class)),
        Boolean.TRUE.equals(row.get("predicate_count_connect_timeout", Boolean.class)),
        Boolean.TRUE.equals(row.get("predicate_count_read_timeout", Boolean.class)),
        Boolean.TRUE.equals(row.get("predicate_count_tls_failure", Boolean.class)),
        Boolean.TRUE.equals(row.get("predicate_count_transport_exception", Boolean.class)),
        Boolean.TRUE.equals(row.get("predicate_count_http_429", Boolean.class)),
        Boolean.TRUE.equals(row.get("enabled", Boolean.class)),
        row.get("created_at", OffsetDateTime.class),
        row.get("updated_at", OffsetDateTime.class));
  }
}
