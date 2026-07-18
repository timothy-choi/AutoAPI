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
public class ServiceInstanceRepositoryCustom {

  private final DatabaseClient databaseClient;

  public ServiceInstanceRepositoryCustom(DatabaseClient databaseClient) {
    this.databaseClient = databaseClient;
  }

  public Mono<ServiceInstanceEntity> upsertRegistration(
      UUID serviceId,
      String instanceId,
      String host,
      int port,
      String scheme,
      String zone,
      String region,
      int weight,
      OffsetDateTime now,
      OffsetDateTime leaseExpiresAt,
      String metadata) {
    return databaseClient
        .sql(
            """
            INSERT INTO service_instances (
              id, service_id, instance_id, host, port, scheme, zone, region, weight,
              status, registration_epoch, registered_at, last_heartbeat_at, lease_expires_at,
              deregistered_at, metadata, created_at, updated_at
            ) VALUES (
              gen_random_uuid(), :serviceId, :instanceId, :host, :port, :scheme, :zone, :region,
              :weight, 'READY', 1, :now, :now, :leaseExpiresAt, NULL, :metadata, :now, :now
            )
            ON CONFLICT (service_id, instance_id) DO UPDATE SET
              host = EXCLUDED.host,
              port = EXCLUDED.port,
              scheme = EXCLUDED.scheme,
              zone = EXCLUDED.zone,
              region = EXCLUDED.region,
              weight = EXCLUDED.weight,
              status = CASE
                WHEN service_instances.status = 'DEREGISTERED'
                  THEN 'READY'
                WHEN service_instances.status IN ('STALE', 'UNHEALTHY', 'STARTING')
                  THEN 'READY'
                ELSE service_instances.status
              END,
              registration_epoch = CASE
                WHEN service_instances.status = 'DEREGISTERED'
                  OR service_instances.host <> EXCLUDED.host
                  OR service_instances.port <> EXCLUDED.port
                  OR service_instances.scheme <> EXCLUDED.scheme
                  THEN service_instances.registration_epoch + 1
                ELSE service_instances.registration_epoch
              END,
              registered_at = CASE
                WHEN service_instances.status = 'DEREGISTERED' THEN EXCLUDED.registered_at
                ELSE service_instances.registered_at
              END,
              last_heartbeat_at = EXCLUDED.last_heartbeat_at,
              lease_expires_at = EXCLUDED.lease_expires_at,
              deregistered_at = NULL,
              metadata = EXCLUDED.metadata,
              updated_at = EXCLUDED.updated_at
            RETURNING id, service_id, instance_id, host, port, scheme, zone, region, weight,
                      status, registration_epoch, registered_at, last_heartbeat_at,
                      lease_expires_at, deregistered_at, metadata, created_at, updated_at
            """)
        .bind("serviceId", serviceId)
        .bind("instanceId", instanceId)
        .bind("host", host)
        .bind("port", port)
        .bind("scheme", scheme)
        .bind("zone", zone)
        .bind("region", region)
        .bind("weight", weight)
        .bind("now", now)
        .bind("leaseExpiresAt", leaseExpiresAt)
        .bind("metadata", metadata)
        .map(this::mapInstance)
        .one();
  }

  public Mono<ServiceInstanceEntity> refreshHeartbeat(
      UUID serviceId, String instanceId, OffsetDateTime now, OffsetDateTime leaseExpiresAt) {
    return databaseClient
        .sql(
            """
            UPDATE service_instances
            SET last_heartbeat_at = :now,
                lease_expires_at = :leaseExpiresAt,
                status = CASE
                  WHEN status IN ('STALE', 'STARTING') THEN 'READY'
                  WHEN status = 'DRAINING' THEN 'DRAINING'
                  WHEN status = 'DEREGISTERED' THEN status
                  ELSE status
                END,
                updated_at = :now
            WHERE service_id = :serviceId
              AND instance_id = :instanceId
              AND status <> 'DEREGISTERED'
            RETURNING id, service_id, instance_id, host, port, scheme, zone, region, weight,
                      status, registration_epoch, registered_at, last_heartbeat_at,
                      lease_expires_at, deregistered_at, metadata, created_at, updated_at
            """)
        .bind("serviceId", serviceId)
        .bind("instanceId", instanceId)
        .bind("now", now)
        .bind("leaseExpiresAt", leaseExpiresAt)
        .map(this::mapInstance)
        .one();
  }

  public Mono<ServiceInstanceEntity> markDraining(
      UUID serviceId, String instanceId, OffsetDateTime now) {
    return databaseClient
        .sql(
            """
            UPDATE service_instances
            SET status = 'DRAINING', updated_at = :now
            WHERE service_id = :serviceId
              AND instance_id = :instanceId
              AND status IN ('READY', 'STARTING', 'UNHEALTHY', 'STALE')
            RETURNING id, service_id, instance_id, host, port, scheme, zone, region, weight,
                      status, registration_epoch, registered_at, last_heartbeat_at,
                      lease_expires_at, deregistered_at, metadata, created_at, updated_at
            """)
        .bind("serviceId", serviceId)
        .bind("instanceId", instanceId)
        .bind("now", now)
        .map(this::mapInstance)
        .one();
  }

  public Mono<ServiceInstanceEntity> deregister(
      UUID serviceId, String instanceId, OffsetDateTime now) {
    return databaseClient
        .sql(
            """
            UPDATE service_instances
            SET status = 'DEREGISTERED',
                deregistered_at = :now,
                updated_at = :now
            WHERE service_id = :serviceId
              AND instance_id = :instanceId
              AND status <> 'DEREGISTERED'
            RETURNING id, service_id, instance_id, host, port, scheme, zone, region, weight,
                      status, registration_epoch, registered_at, last_heartbeat_at,
                      lease_expires_at, deregistered_at, metadata, created_at, updated_at
            """)
        .bind("serviceId", serviceId)
        .bind("instanceId", instanceId)
        .bind("now", now)
        .map(this::mapInstance)
        .one()
        .switchIfEmpty(findByServiceIdAndInstanceId(serviceId, instanceId));
  }

  public Flux<ServiceInstanceEntity> transitionExpiredLeasesToStale(
      OffsetDateTime now, int batchSize) {
    return databaseClient
        .sql(
            """
            UPDATE service_instances
            SET status = 'STALE', updated_at = :now
            WHERE id IN (
              SELECT id FROM service_instances
              WHERE status IN ('READY', 'STARTING', 'UNHEALTHY')
                AND lease_expires_at < :now
              ORDER BY lease_expires_at
              LIMIT :batchSize
              FOR UPDATE SKIP LOCKED
            )
            RETURNING id, service_id, instance_id, host, port, scheme, zone, region, weight,
                      status, registration_epoch, registered_at, last_heartbeat_at,
                      lease_expires_at, deregistered_at, metadata, created_at, updated_at
            """)
        .bind("now", now)
        .bind("batchSize", batchSize)
        .map(this::mapInstance)
        .all();
  }

  public Flux<ServiceInstanceEntity> listFiltered(
      UUID serviceId,
      String status,
      String zone,
      String region,
      String instanceId,
      int limit,
      int offset) {
    StringBuilder sql =
        new StringBuilder(
            """
            SELECT id, service_id, instance_id, host, port, scheme, zone, region, weight,
                   status, registration_epoch, registered_at, last_heartbeat_at,
                   lease_expires_at, deregistered_at, metadata, created_at, updated_at
            FROM service_instances
            WHERE service_id = :serviceId
            """);
    List<String> clauses = new ArrayList<>();
    if (status != null && !status.isBlank()) {
      clauses.add("status = :status");
    }
    if (zone != null && !zone.isBlank()) {
      clauses.add("zone = :zone");
    }
    if (region != null && !region.isBlank()) {
      clauses.add("region = :region");
    }
    if (instanceId != null && !instanceId.isBlank()) {
      clauses.add("instance_id = :instanceId");
    }
    for (String clause : clauses) {
      sql.append(" AND ").append(clause);
    }
    sql.append(" ORDER BY instance_id LIMIT :limit OFFSET :offset");

    DatabaseClient.GenericExecuteSpec spec =
        databaseClient.sql(sql.toString()).bind("serviceId", serviceId);
    if (status != null && !status.isBlank()) {
      spec = spec.bind("status", status);
    }
    if (zone != null && !zone.isBlank()) {
      spec = spec.bind("zone", zone);
    }
    if (region != null && !region.isBlank()) {
      spec = spec.bind("region", region);
    }
    if (instanceId != null && !instanceId.isBlank()) {
      spec = spec.bind("instanceId", instanceId);
    }
    return spec.bind("limit", limit).bind("offset", offset).map(this::mapInstance).all();
  }

  public Mono<ServiceInstanceEntity> findByServiceIdAndInstanceId(
      UUID serviceId, String instanceId) {
    return databaseClient
        .sql(
            """
            SELECT id, service_id, instance_id, host, port, scheme, zone, region, weight,
                   status, registration_epoch, registered_at, last_heartbeat_at,
                   lease_expires_at, deregistered_at, metadata, created_at, updated_at
            FROM service_instances
            WHERE service_id = :serviceId AND instance_id = :instanceId
            """)
        .bind("serviceId", serviceId)
        .bind("instanceId", instanceId)
        .map(this::mapInstance)
        .one();
  }

  public Flux<ServiceInstanceEntity> findEligibleByServiceId(UUID serviceId, OffsetDateTime now) {
    return databaseClient
        .sql(
            """
            SELECT id, service_id, instance_id, host, port, scheme, zone, region, weight,
                   status, registration_epoch, registered_at, last_heartbeat_at,
                   lease_expires_at, deregistered_at, metadata, created_at, updated_at
            FROM service_instances
            WHERE service_id = :serviceId
              AND status = 'READY'
              AND lease_expires_at >= :now
            ORDER BY instance_id
            """)
        .bind("serviceId", serviceId)
        .bind("now", now)
        .map(this::mapInstance)
        .all();
  }

  private ServiceInstanceEntity mapInstance(
      io.r2dbc.spi.Readable row, io.r2dbc.spi.RowMetadata metadata) {
    return new ServiceInstanceEntity(
        row.get("id", UUID.class),
        row.get("service_id", UUID.class),
        row.get("instance_id", String.class),
        row.get("host", String.class),
        row.get("port", Integer.class),
        row.get("scheme", String.class),
        row.get("zone", String.class),
        row.get("region", String.class),
        row.get("weight", Integer.class),
        row.get("status", String.class),
        row.get("registration_epoch", Long.class),
        row.get("registered_at", OffsetDateTime.class),
        row.get("last_heartbeat_at", OffsetDateTime.class),
        row.get("lease_expires_at", OffsetDateTime.class),
        row.get("deregistered_at", OffsetDateTime.class),
        row.get("metadata", String.class),
        row.get("created_at", OffsetDateTime.class),
        row.get("updated_at", OffsetDateTime.class));
  }
}
