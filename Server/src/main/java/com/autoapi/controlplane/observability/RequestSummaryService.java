package com.autoapi.controlplane.observability;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@ConditionalOnProperty(
    name = "autoapi.controlplane.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class RequestSummaryService {

  private static final int MAX_LIMIT = 200;
  private static final int MAX_BATCH = 20;

  private final DatabaseClient databaseClient;

  public RequestSummaryService(DatabaseClient databaseClient) {
    this.databaseClient = databaseClient;
  }

  public Mono<Void> ingestBatch(
      String gatewayId, String instanceId, List<RequestSummaryPayload> batch) {
    if (batch == null || batch.isEmpty()) {
      return Mono.empty();
    }
    List<RequestSummaryPayload> limited =
        batch.size() > MAX_BATCH ? batch.subList(0, MAX_BATCH) : batch;
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    return Flux.fromIterable(limited)
        .concatMap(
            summary ->
                databaseClient
                    .sql(
                        """
                        INSERT INTO request_summaries (
                            id, gateway_id, instance_id, request_id, trace_id, api_id, route_id,
                            method, status, duration_ms, attempt_count, retry_count, fallback_used, created_at
                        ) VALUES (
                            :id, :gatewayId, :instanceId, :requestId, :traceId, :apiId, :routeId,
                            :method, :status, :durationMs, :attemptCount, :retryCount, :fallbackUsed, :createdAt
                        )
                        """)
                    .bind("id", UUID.randomUUID())
                    .bind("gatewayId", gatewayId)
                    .bind("instanceId", instanceId)
                    .bind("requestId", truncate(summary.requestId(), 128))
                    .bind("traceId", truncate(summary.traceId(), 64))
                    .bind("apiId", summary.apiId())
                    .bind("routeId", truncate(summary.routeId(), 128))
                    .bind("method", truncate(summary.method(), 16))
                    .bind("status", summary.status())
                    .bind("durationMs", summary.durationMs())
                    .bind("attemptCount", summary.attemptCount())
                    .bind("retryCount", summary.retryCount())
                    .bind("fallbackUsed", summary.fallbackUsed())
                    .bind("createdAt", now)
                    .fetch()
                    .rowsUpdated()
                    .then())
        .then();
  }

  public Flux<RequestSummaryView> listSummaries(String gatewayId, int limit) {
    int effectiveLimit = Math.max(1, Math.min(limit, MAX_LIMIT));
    if (gatewayId != null && !gatewayId.isBlank()) {
      return databaseClient
          .sql(
              """
              SELECT gateway_id, instance_id, request_id, trace_id, api_id, route_id,
                     method, status, duration_ms, attempt_count, retry_count, fallback_used, created_at
              FROM request_summaries
              WHERE gateway_id = :gatewayId
              ORDER BY created_at DESC
              LIMIT :limit
              """)
          .bind("gatewayId", gatewayId)
          .bind("limit", effectiveLimit)
          .map(this::toView)
          .all();
    }
    return databaseClient
        .sql(
            """
            SELECT gateway_id, instance_id, request_id, trace_id, api_id, route_id,
                   method, status, duration_ms, attempt_count, retry_count, fallback_used, created_at
            FROM request_summaries
            ORDER BY created_at DESC
            LIMIT :limit
            """)
        .bind("limit", effectiveLimit)
        .map(this::toView)
        .all();
  }

  private RequestSummaryView toView(io.r2dbc.spi.Row row, io.r2dbc.spi.RowMetadata metadata) {
    return new RequestSummaryView(
        row.get("request_id", String.class),
        row.get("trace_id", String.class),
        row.get("gateway_id", String.class),
        row.get("api_id", UUID.class),
        row.get("route_id", String.class),
        row.get("method", String.class),
        row.get("status", Integer.class),
        row.get("duration_ms", Long.class),
        row.get("attempt_count", Integer.class),
        row.get("retry_count", Integer.class),
        row.get("fallback_used", Boolean.class),
        row.get("created_at", OffsetDateTime.class).toString());
  }

  private static String truncate(String value, int maxLength) {
    if (value == null) {
      return "";
    }
    return value.length() <= maxLength ? value : value.substring(0, maxLength);
  }

  public record RequestSummaryPayload(
      String requestId,
      String traceId,
      UUID apiId,
      String routeId,
      String method,
      int status,
      long durationMs,
      int attemptCount,
      int retryCount,
      boolean fallbackUsed) {}

  public record RequestSummaryView(
      String requestId,
      String traceId,
      String gatewayId,
      UUID apiId,
      String routeId,
      String method,
      int status,
      long durationMs,
      int attemptCount,
      int retryCount,
      boolean fallbackUsed,
      String timestamp) {}
}
