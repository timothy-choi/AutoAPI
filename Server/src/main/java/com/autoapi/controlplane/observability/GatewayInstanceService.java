package com.autoapi.controlplane.observability;

import com.autoapi.controlplane.ControlPlaneProperties;
import com.autoapi.controlplane.api.ControlPlaneException;
import com.autoapi.controlplane.persistence.GatewayEntity;
import com.autoapi.controlplane.persistence.GatewayRepository;
import io.r2dbc.postgresql.codec.Json;
import java.time.Duration;
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
public class GatewayInstanceService {

  private final GatewayRepository gatewayRepository;
  private final DatabaseClient databaseClient;
  private final ControlPlaneProperties controlPlaneProperties;

  public GatewayInstanceService(
      GatewayRepository gatewayRepository,
      DatabaseClient databaseClient,
      ControlPlaneProperties controlPlaneProperties) {
    this.gatewayRepository = gatewayRepository;
    this.databaseClient = databaseClient;
    this.controlPlaneProperties = controlPlaneProperties;
  }

  public Mono<Void> upsertHeartbeat(GatewayHeartbeatPayload payload) {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    UUID rowId =
        UUID.nameUUIDFromBytes((payload.gatewayId() + ":" + payload.instanceId()).getBytes());
    return databaseClient
        .sql(
            """
            INSERT INTO gateway_instances (
                id, gateway_id, instance_id, status, started_at, last_seen_at,
                software_version, active_snapshot_version, active_snapshot_activated_at,
                route_count, target_count, uptime_seconds, metadata, created_at, updated_at
            ) VALUES (
                :id, :gatewayId, :instanceId, :status, :startedAt, :lastSeenAt,
                :softwareVersion, :activeSnapshotVersion, :activeSnapshotActivatedAt,
                :routeCount, :targetCount, :uptimeSeconds, :metadata, :createdAt, :updatedAt
            )
            ON CONFLICT (gateway_id, instance_id) DO UPDATE SET
                status = EXCLUDED.status,
                last_seen_at = EXCLUDED.last_seen_at,
                software_version = EXCLUDED.software_version,
                active_snapshot_version = EXCLUDED.active_snapshot_version,
                active_snapshot_activated_at = EXCLUDED.active_snapshot_activated_at,
                route_count = EXCLUDED.route_count,
                target_count = EXCLUDED.target_count,
                uptime_seconds = EXCLUDED.uptime_seconds,
                metadata = EXCLUDED.metadata,
                updated_at = EXCLUDED.updated_at
            """)
        .bind("id", rowId)
        .bind("gatewayId", payload.gatewayId())
        .bind("instanceId", payload.instanceId())
        .bind("status", payload.status())
        .bind("startedAt", payload.startedAt())
        .bind("lastSeenAt", now)
        .bind("softwareVersion", payload.softwareVersion())
        .bind("activeSnapshotVersion", payload.activeSnapshotVersion())
        .bind("activeSnapshotActivatedAt", payload.activeSnapshotActivatedAt())
        .bind("routeCount", payload.routeCount())
        .bind("targetCount", payload.targetCount())
        .bind("uptimeSeconds", payload.uptimeSeconds())
        .bind("metadata", Json.of(payload.metadataJson() == null ? "{}" : payload.metadataJson()))
        .bind("createdAt", now)
        .bind("updatedAt", now)
        .fetch()
        .rowsUpdated()
        .then();
  }

  public Flux<GatewayInstanceView> listInstances(String gatewayId, String statusFilter) {
    StringBuilder sql =
        new StringBuilder(
            """
            SELECT gateway_id, instance_id, status, started_at, last_seen_at,
                   software_version, active_snapshot_version, active_snapshot_activated_at,
                   route_count, target_count, uptime_seconds
            FROM gateway_instances
            WHERE gateway_id = :gatewayId
            """);
    if (statusFilter != null && !statusFilter.isBlank()) {
      sql.append(" AND status = :status");
    }
    sql.append(" ORDER BY last_seen_at DESC");
    var spec = databaseClient.sql(sql.toString()).bind("gatewayId", gatewayId);
    if (statusFilter != null && !statusFilter.isBlank()) {
      spec = spec.bind("status", statusFilter.toUpperCase());
    }
    OffsetDateTime staleCutoff =
        OffsetDateTime.now(ZoneOffset.UTC).minus(controlPlaneProperties.gatewayStaleAfter());
    Duration offlineAfter = controlPlaneProperties.gatewayStaleAfter().multipliedBy(3);
    OffsetDateTime offlineCutoff = OffsetDateTime.now(ZoneOffset.UTC).minus(offlineAfter);
    return spec.map(
            (row, metadata) ->
                new GatewayInstanceView(
                    row.get("gateway_id", String.class),
                    row.get("instance_id", String.class),
                    deriveOperationalStatus(
                        row.get("last_seen_at", OffsetDateTime.class), staleCutoff, offlineCutoff),
                    row.get("started_at", OffsetDateTime.class).toString(),
                    row.get("last_seen_at", OffsetDateTime.class).toString(),
                    row.get("software_version", String.class),
                    row.get("active_snapshot_version", Long.class),
                    row.get("active_snapshot_activated_at", OffsetDateTime.class) == null
                        ? null
                        : row.get("active_snapshot_activated_at", OffsetDateTime.class).toString(),
                    row.get("route_count", Integer.class),
                    row.get("target_count", Integer.class),
                    row.get("uptime_seconds", Long.class)))
        .all();
  }

  public Flux<ManagedGatewayView> listManagedGateways(
      String gatewayIdFilter, String statusFilter, Long activeSnapshotVersionFilter) {
    OffsetDateTime staleCutoff =
        OffsetDateTime.now(ZoneOffset.UTC).minus(controlPlaneProperties.gatewayStaleAfter());
    Duration offlineAfter = controlPlaneProperties.gatewayStaleAfter().multipliedBy(3);
    OffsetDateTime offlineCutoff = OffsetDateTime.now(ZoneOffset.UTC).minus(offlineAfter);

    Flux<GatewayEntity> gateways =
        gatewayIdFilter == null || gatewayIdFilter.isBlank()
            ? gatewayRepository.findAll()
            : gatewayRepository.findById(gatewayIdFilter).flux();

    return gateways.flatMap(
        gateway ->
            databaseClient
                .sql(
                    """
                    SELECT COUNT(*) AS instance_count,
                           MAX(last_seen_at) AS latest_seen,
                           MAX(active_snapshot_version) AS active_snapshot_version,
                           SUM(route_count) AS route_count,
                           SUM(target_count) AS target_count
                    FROM gateway_instances
                    WHERE gateway_id = :gatewayId
                    """)
                .bind("gatewayId", gateway.id())
                .map(
                    (row, metadata) ->
                        new ManagedGatewayView(
                            gateway.id(),
                            gateway.gatewayGroup(),
                            gateway.softwareVersion(),
                            deriveGatewayStatus(
                                row.get("latest_seen", OffsetDateTime.class),
                                staleCutoff,
                                offlineCutoff),
                            gateway.startedAt().toString(),
                            row.get("latest_seen", OffsetDateTime.class) == null
                                ? gateway.lastSeenAt().toString()
                                : row.get("latest_seen", OffsetDateTime.class).toString(),
                            row.get("active_snapshot_version", Long.class),
                            row.get("instance_count", Long.class) == null
                                ? 0
                                : row.get("instance_count", Long.class).intValue(),
                            row.get("route_count", Long.class) == null
                                ? 0
                                : row.get("route_count", Long.class).intValue(),
                            row.get("target_count", Long.class) == null
                                ? 0
                                : row.get("target_count", Long.class).intValue()))
                .one()
                .filter(
                    view ->
                        statusFilter == null
                            || statusFilter.isBlank()
                            || view.operationalStatus().equalsIgnoreCase(statusFilter))
                .filter(
                    view ->
                        activeSnapshotVersionFilter == null
                            || (view.activeSnapshotVersion() != null
                                && view.activeSnapshotVersion()
                                    .equals(activeSnapshotVersionFilter))));
  }

  public Mono<ManagedGatewayDetail> getManagedGateway(String gatewayId) {
    return listManagedGateways(gatewayId, null, null)
        .next()
        .switchIfEmpty(Mono.error(ControlPlaneException.notFound("Gateway was not found")))
        .flatMap(
            summary ->
                listInstances(gatewayId, null)
                    .collectList()
                    .map(instances -> new ManagedGatewayDetail(summary, instances)));
  }

  public static String deriveOperationalStatus(
      OffsetDateTime lastSeenAt, OffsetDateTime staleCutoff, OffsetDateTime offlineCutoff) {
    if (lastSeenAt == null || lastSeenAt.isBefore(offlineCutoff)) {
      return "OFFLINE";
    }
    if (lastSeenAt.isBefore(staleCutoff)) {
      return "STALE";
    }
    return "READY";
  }

  private static String deriveGatewayStatus(
      OffsetDateTime lastSeenAt, OffsetDateTime staleCutoff, OffsetDateTime offlineCutoff) {
    return deriveOperationalStatus(lastSeenAt, staleCutoff, offlineCutoff);
  }

  public record GatewayHeartbeatPayload(
      String gatewayId,
      String instanceId,
      String status,
      OffsetDateTime startedAt,
      String softwareVersion,
      Long activeSnapshotVersion,
      OffsetDateTime activeSnapshotActivatedAt,
      int routeCount,
      int targetCount,
      long uptimeSeconds,
      String metadataJson) {}

  public record GatewayInstanceView(
      String gatewayId,
      String instanceId,
      String operationalStatus,
      String startedAt,
      String lastSeenAt,
      String softwareVersion,
      Long activeSnapshotVersion,
      String activeSnapshotActivatedAt,
      int routeCount,
      int targetCount,
      long uptimeSeconds) {}

  public record ManagedGatewayView(
      String gatewayId,
      String gatewayGroup,
      String softwareVersion,
      String operationalStatus,
      String startedAt,
      String lastSeenAt,
      Long activeSnapshotVersion,
      int instanceCount,
      int routeCount,
      int targetCount) {}

  public record ManagedGatewayDetail(
      ManagedGatewayView gateway, List<GatewayInstanceView> instances) {}
}
