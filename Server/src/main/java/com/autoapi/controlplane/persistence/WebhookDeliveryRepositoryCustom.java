package com.autoapi.controlplane.persistence;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
@ConditionalOnProperty(
    name = "autoapi.controlplane.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class WebhookDeliveryRepositoryCustom {

  private final DatabaseClient databaseClient;

  public WebhookDeliveryRepositoryCustom(DatabaseClient databaseClient) {
    this.databaseClient = databaseClient;
  }

  public Mono<WebhookDeliveryEntity> createPending(
      UUID subscriptionId,
      UUID eventId,
      int secretVersion,
      OffsetDateTime now,
      UUID replayOfDeliveryId) {
    UUID id = UUID.randomUUID();
    return databaseClient
        .sql(
            """
            INSERT INTO webhook_deliveries (
              id, subscription_id, event_id, status, attempt_count, next_attempt_at,
              secret_version, replay_of_delivery_id, created_at, updated_at
            ) VALUES (
              :id, :subscriptionId, :eventId, 'PENDING', 0, :nextAttemptAt,
              :secretVersion, :replayOfDeliveryId, :createdAt, :updatedAt
            )
            ON CONFLICT (subscription_id, event_id) DO NOTHING
            RETURNING id, subscription_id, event_id, status, attempt_count, next_attempt_at,
                      last_attempt_at, delivered_at, dead_lettered_at, last_status_code,
                      last_error_type, last_error_summary, secret_version, replay_of_delivery_id,
                      created_at, updated_at
            """)
        .bind("id", id)
        .bind("subscriptionId", subscriptionId)
        .bind("eventId", eventId)
        .bind("nextAttemptAt", now)
        .bind("secretVersion", secretVersion)
        .bind("replayOfDeliveryId", replayOfDeliveryId)
        .bind("createdAt", now)
        .bind("updatedAt", now)
        .map(this::mapDelivery)
        .one()
        .switchIfEmpty(findBySubscriptionAndEvent(subscriptionId, eventId));
  }

  public Mono<WebhookDeliveryEntity> findBySubscriptionAndEvent(UUID subscriptionId, UUID eventId) {
    return databaseClient
        .sql(
            """
            SELECT id, subscription_id, event_id, status, attempt_count, next_attempt_at,
                   last_attempt_at, delivered_at, dead_lettered_at, last_status_code,
                   last_error_type, last_error_summary, secret_version, replay_of_delivery_id,
                   created_at, updated_at
            FROM webhook_deliveries
            WHERE subscription_id = :subscriptionId AND event_id = :eventId
            """)
        .bind("subscriptionId", subscriptionId)
        .bind("eventId", eventId)
        .map(this::mapDelivery)
        .one();
  }

  public Mono<WebhookDeliveryEntity> findById(UUID projectId, UUID deliveryId) {
    return databaseClient
        .sql(
            """
            SELECT d.id, d.subscription_id, d.event_id, d.status, d.attempt_count, d.next_attempt_at,
                   d.last_attempt_at, d.delivered_at, d.dead_lettered_at, d.last_status_code,
                   d.last_error_type, d.last_error_summary, d.secret_version, d.replay_of_delivery_id,
                   d.created_at, d.updated_at
            FROM webhook_deliveries d
            JOIN webhook_subscriptions s ON s.id = d.subscription_id
            WHERE d.id = :deliveryId AND s.project_id = :projectId
            """)
        .bind("deliveryId", deliveryId)
        .bind("projectId", projectId)
        .map(this::mapDelivery)
        .one();
  }

  public Flux<WebhookDeliveryEntity> list(
      UUID projectId,
      UUID subscriptionId,
      UUID eventId,
      String status,
      OffsetDateTime createdAfter,
      OffsetDateTime createdBefore,
      int limit) {
    StringBuilder sql =
        new StringBuilder(
            """
            SELECT d.id, d.subscription_id, d.event_id, d.status, d.attempt_count, d.next_attempt_at,
                   d.last_attempt_at, d.delivered_at, d.dead_lettered_at, d.last_status_code,
                   d.last_error_type, d.last_error_summary, d.secret_version, d.replay_of_delivery_id,
                   d.created_at, d.updated_at
            FROM webhook_deliveries d
            JOIN webhook_subscriptions s ON s.id = d.subscription_id
            WHERE s.project_id = :projectId
            """);
    List<String> clauses = new ArrayList<>();
    if (subscriptionId != null) {
      clauses.add("d.subscription_id = :subscriptionId");
    }
    if (eventId != null) {
      clauses.add("d.event_id = :eventId");
    }
    if (status != null && !status.isBlank()) {
      clauses.add("d.status = :status");
    }
    if (createdAfter != null) {
      clauses.add("d.created_at >= :createdAfter");
    }
    if (createdBefore != null) {
      clauses.add("d.created_at <= :createdBefore");
    }
    for (String clause : clauses) {
      sql.append(" AND ").append(clause);
    }
    sql.append(" ORDER BY d.created_at DESC LIMIT :limit");

    DatabaseClient.GenericExecuteSpec spec =
        databaseClient.sql(sql.toString()).bind("projectId", projectId);
    if (subscriptionId != null) {
      spec = spec.bind("subscriptionId", subscriptionId);
    }
    if (eventId != null) {
      spec = spec.bind("eventId", eventId);
    }
    if (status != null && !status.isBlank()) {
      spec = spec.bind("status", status);
    }
    if (createdAfter != null) {
      spec = spec.bind("createdAfter", createdAfter);
    }
    if (createdBefore != null) {
      spec = spec.bind("createdBefore", createdBefore);
    }
    return spec.bind("limit", limit).map(this::mapDelivery).all();
  }

  public Flux<WebhookDeliveryEntity> claimDueDeliveries(int batchSize, OffsetDateTime now) {
    return databaseClient
        .sql(
            """
            UPDATE webhook_deliveries
            SET status = 'IN_PROGRESS', updated_at = :now
            WHERE id IN (
              SELECT id FROM webhook_deliveries
              WHERE status IN ('PENDING', 'RETRY_SCHEDULED')
                AND next_attempt_at <= :now
              ORDER BY next_attempt_at
              LIMIT :batchSize
              FOR UPDATE SKIP LOCKED
            )
            RETURNING id, subscription_id, event_id, status, attempt_count, next_attempt_at,
                      last_attempt_at, delivered_at, dead_lettered_at, last_status_code,
                      last_error_type, last_error_summary, secret_version, replay_of_delivery_id,
                      created_at, updated_at
            """)
        .bind("now", now)
        .bind("batchSize", batchSize)
        .map(this::mapDelivery)
        .all();
  }

  public Mono<WebhookDeliveryEntity> markSucceeded(
      UUID deliveryId, int statusCode, OffsetDateTime now, int attemptNumber) {
    return databaseClient
        .sql(
            """
            UPDATE webhook_deliveries SET
              status = 'SUCCEEDED', attempt_count = :attemptCount, last_attempt_at = :now,
              delivered_at = :now, last_status_code = :statusCode, last_error_type = NULL,
              last_error_summary = NULL, updated_at = :now
            WHERE id = :id
            RETURNING id, subscription_id, event_id, status, attempt_count, next_attempt_at,
                      last_attempt_at, delivered_at, dead_lettered_at, last_status_code,
                      last_error_type, last_error_summary, secret_version, replay_of_delivery_id,
                      created_at, updated_at
            """)
        .bind("attemptCount", attemptNumber)
        .bind("now", now)
        .bind("statusCode", statusCode)
        .bind("id", deliveryId)
        .map(this::mapDelivery)
        .one();
  }

  public Mono<WebhookDeliveryEntity> scheduleRetry(
      UUID deliveryId,
      int attemptNumber,
      int statusCode,
      String errorType,
      String errorSummary,
      OffsetDateTime nextAttemptAt,
      OffsetDateTime now) {
    return databaseClient
        .sql(
            """
            UPDATE webhook_deliveries SET
              status = 'RETRY_SCHEDULED', attempt_count = :attemptCount, last_attempt_at = :now,
              next_attempt_at = :nextAttemptAt, last_status_code = :statusCode,
              last_error_type = :errorType, last_error_summary = :errorSummary, updated_at = :now
            WHERE id = :id
            RETURNING id, subscription_id, event_id, status, attempt_count, next_attempt_at,
                      last_attempt_at, delivered_at, dead_lettered_at, last_status_code,
                      last_error_type, last_error_summary, secret_version, replay_of_delivery_id,
                      created_at, updated_at
            """)
        .bind("attemptCount", attemptNumber)
        .bind("now", now)
        .bind("nextAttemptAt", nextAttemptAt)
        .bind("statusCode", statusCode)
        .bind("errorType", errorType)
        .bind("errorSummary", errorSummary)
        .bind("id", deliveryId)
        .map(this::mapDelivery)
        .one();
  }

  public Mono<WebhookDeliveryEntity> markDeadLettered(
      UUID deliveryId,
      int attemptNumber,
      Integer statusCode,
      String errorType,
      String errorSummary,
      OffsetDateTime now) {
    return databaseClient
        .sql(
            """
            UPDATE webhook_deliveries SET
              status = 'DEAD_LETTERED', attempt_count = :attemptCount, last_attempt_at = :now,
              dead_lettered_at = :now, last_status_code = :statusCode, last_error_type = :errorType,
              last_error_summary = :errorSummary, updated_at = :now
            WHERE id = :id
            RETURNING id, subscription_id, event_id, status, attempt_count, next_attempt_at,
                      last_attempt_at, delivered_at, dead_lettered_at, last_status_code,
                      last_error_type, last_error_summary, secret_version, replay_of_delivery_id,
                      created_at, updated_at
            """)
        .bind("attemptCount", attemptNumber)
        .bind("now", now)
        .bind("statusCode", statusCode)
        .bind("errorType", errorType)
        .bind("errorSummary", errorSummary)
        .bind("id", deliveryId)
        .map(this::mapDelivery)
        .one();
  }

  public Mono<WebhookDeliveryEntity> resetForReplay(UUID deliveryId, OffsetDateTime now) {
    return databaseClient
        .sql(
            """
            UPDATE webhook_deliveries SET
              status = 'PENDING', next_attempt_at = :now, updated_at = :now
            WHERE id = :id AND status = 'DEAD_LETTERED'
            RETURNING id, subscription_id, event_id, status, attempt_count, next_attempt_at,
                      last_attempt_at, delivered_at, dead_lettered_at, last_status_code,
                      last_error_type, last_error_summary, secret_version, replay_of_delivery_id,
                      created_at, updated_at
            """)
        .bind("now", now)
        .bind("id", deliveryId)
        .map(this::mapDelivery)
        .one();
  }

  public Mono<WebhookDeliveryAttemptEntity> insertAttempt(
      UUID deliveryId,
      int attemptNumber,
      OffsetDateTime startedAt,
      OffsetDateTime completedAt,
      Integer durationMs,
      Integer statusCode,
      String result,
      String errorType,
      String responsePreview) {
    UUID id = UUID.randomUUID();
    return databaseClient
        .sql(
            """
            INSERT INTO webhook_delivery_attempts (
              id, delivery_id, attempt_number, started_at, completed_at, duration_ms,
              status_code, result, error_type, response_body_preview, created_at
            ) VALUES (
              :id, :deliveryId, :attemptNumber, :startedAt, :completedAt, :durationMs,
              :statusCode, :result, :errorType, :responsePreview, :createdAt
            )
            RETURNING id, delivery_id, attempt_number, started_at, completed_at, duration_ms,
                      status_code, result, error_type, response_body_preview, created_at
            """)
        .bind("id", id)
        .bind("deliveryId", deliveryId)
        .bind("attemptNumber", attemptNumber)
        .bind("startedAt", startedAt)
        .bind("completedAt", completedAt)
        .bind("durationMs", durationMs)
        .bind("statusCode", statusCode)
        .bind("result", result)
        .bind("errorType", errorType)
        .bind("responsePreview", responsePreview)
        .bind("createdAt", completedAt)
        .map(this::mapAttempt)
        .one();
  }

  public Flux<WebhookDeliveryAttemptEntity> listAttempts(UUID deliveryId) {
    return databaseClient
        .sql(
            """
            SELECT id, delivery_id, attempt_number, started_at, completed_at, duration_ms,
                   status_code, result, error_type, response_body_preview, created_at
            FROM webhook_delivery_attempts
            WHERE delivery_id = :deliveryId ORDER BY attempt_number ASC
            """)
        .bind("deliveryId", deliveryId)
        .map(this::mapAttempt)
        .all();
  }

  public Mono<Long> countPending() {
    return databaseClient
        .sql(
            """
            SELECT COUNT(*) AS cnt FROM webhook_deliveries
            WHERE status IN ('PENDING', 'RETRY_SCHEDULED')
            """)
        .map((row, meta) -> row.get("cnt", Long.class))
        .one();
  }

  private WebhookDeliveryEntity mapDelivery(
      io.r2dbc.spi.Readable row, io.r2dbc.spi.RowMetadata metadata) {
    return new WebhookDeliveryEntity(
        row.get("id", UUID.class),
        row.get("subscription_id", UUID.class),
        row.get("event_id", UUID.class),
        row.get("status", String.class),
        row.get("attempt_count", Integer.class),
        row.get("next_attempt_at", OffsetDateTime.class),
        row.get("last_attempt_at", OffsetDateTime.class),
        row.get("delivered_at", OffsetDateTime.class),
        row.get("dead_lettered_at", OffsetDateTime.class),
        row.get("last_status_code", Integer.class),
        row.get("last_error_type", String.class),
        row.get("last_error_summary", String.class),
        row.get("secret_version", Integer.class),
        row.get("replay_of_delivery_id", UUID.class),
        row.get("created_at", OffsetDateTime.class),
        row.get("updated_at", OffsetDateTime.class));
  }

  private WebhookDeliveryAttemptEntity mapAttempt(
      io.r2dbc.spi.Readable row, io.r2dbc.spi.RowMetadata metadata) {
    return new WebhookDeliveryAttemptEntity(
        row.get("id", UUID.class),
        row.get("delivery_id", UUID.class),
        row.get("attempt_number", Integer.class),
        row.get("started_at", OffsetDateTime.class),
        row.get("completed_at", OffsetDateTime.class),
        row.get("duration_ms", Integer.class),
        row.get("status_code", Integer.class),
        row.get("result", String.class),
        row.get("error_type", String.class),
        row.get("response_body_preview", String.class),
        row.get("created_at", OffsetDateTime.class));
  }
}
