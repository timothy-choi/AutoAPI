package com.autoapi.controlplane.gateway;

import com.autoapi.controlplane.api.ControlPlaneException;
import com.autoapi.controlplane.gateway.GatewayRegistrationService.RegistrationResult;
import com.autoapi.controlplane.persistence.GatewayEntity;
import com.autoapi.controlplane.persistence.GatewayRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.r2dbc.postgresql.codec.Json;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@ConditionalOnProperty(
    name = "autoapi.controlplane.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class GatewayRegistrationService {

  private static final int MAX_METADATA_BYTES = 4096;

  private final GatewayRepository gatewayRepository;
  private final DatabaseClient databaseClient;
  private final ObjectMapper objectMapper;

  public GatewayRegistrationService(
      GatewayRepository gatewayRepository,
      DatabaseClient databaseClient,
      ObjectMapper objectMapper) {
    this.gatewayRepository = gatewayRepository;
    this.databaseClient = databaseClient;
    this.objectMapper = objectMapper;
  }

  public Mono<RegistrationResult> register(
      String gatewayId,
      String gatewayGroup,
      String softwareVersion,
      OffsetDateTime startedAt,
      Map<String, Object> metadata) {
    GatewayIdValidator.validate(gatewayId);
    String effectiveGroup =
        gatewayGroup == null || gatewayGroup.isBlank() ? "default" : gatewayGroup;
    if (softwareVersion == null || softwareVersion.isBlank()) {
      return Mono.error(ControlPlaneException.invalidRequest("softwareVersion is required"));
    }
    if (startedAt == null) {
      return Mono.error(ControlPlaneException.invalidRequest("startedAt is required"));
    }
    Map<String, Object> safeMetadata = metadata == null ? Map.of() : metadata;
    return serializeMetadata(safeMetadata)
        .flatMap(
            metadataJson ->
                gatewayRepository
                    .findById(gatewayId)
                    .flatMap(
                        existing ->
                            updateExisting(
                                gatewayId,
                                effectiveGroup,
                                softwareVersion,
                                startedAt,
                                metadataJson,
                                existing.registeredAt(),
                                existing.createdAt()))
                    .switchIfEmpty(
                        insertNew(
                            gatewayId, effectiveGroup, softwareVersion, startedAt, metadataJson)));
  }

  private Mono<RegistrationResult> insertNew(
      String gatewayId,
      String gatewayGroup,
      String softwareVersion,
      OffsetDateTime startedAt,
      Json metadataJson) {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    return databaseClient
        .sql(
            """
            INSERT INTO gateways (
                id, gateway_group, software_version, started_at, registered_at,
                last_seen_at, metadata, created_at, updated_at
            ) VALUES (
                :id, :gatewayGroup, :softwareVersion, :startedAt, :registeredAt,
                :lastSeenAt, :metadata, :createdAt, :updatedAt
            )
            """)
        .bind("id", gatewayId)
        .bind("gatewayGroup", gatewayGroup)
        .bind("softwareVersion", softwareVersion)
        .bind("startedAt", startedAt)
        .bind("registeredAt", now)
        .bind("lastSeenAt", now)
        .bind("metadata", metadataJson)
        .bind("createdAt", now)
        .bind("updatedAt", now)
        .fetch()
        .rowsUpdated()
        .flatMap(
            rows ->
                rows != null && rows == 1L
                    ? gatewayRepository
                        .findById(gatewayId)
                        .map(entity -> RegistrationResult.from(entity, true))
                    : Mono.error(ControlPlaneException.internal("Failed to register gateway")));
  }

  private Mono<RegistrationResult> updateExisting(
      String gatewayId,
      String gatewayGroup,
      String softwareVersion,
      OffsetDateTime startedAt,
      Json metadataJson,
      OffsetDateTime registeredAt,
      OffsetDateTime createdAt) {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    return databaseClient
        .sql(
            """
            UPDATE gateways
            SET gateway_group = :gatewayGroup,
                software_version = :softwareVersion,
                started_at = :startedAt,
                last_seen_at = :lastSeenAt,
                metadata = :metadata,
                updated_at = :updatedAt
            WHERE id = :id
            """)
        .bind("id", gatewayId)
        .bind("gatewayGroup", gatewayGroup)
        .bind("softwareVersion", softwareVersion)
        .bind("startedAt", startedAt)
        .bind("lastSeenAt", now)
        .bind("metadata", metadataJson)
        .bind("updatedAt", now)
        .fetch()
        .rowsUpdated()
        .flatMap(
            rows ->
                rows != null && rows == 1L
                    ? Mono.just(
                        new RegistrationResult(
                            gatewayId, gatewayGroup, softwareVersion, registeredAt, now, false))
                    : Mono.error(ControlPlaneException.internal("Failed to re-register gateway")));
  }

  public Mono<Void> touchLastSeen(String gatewayId) {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    return databaseClient
        .sql(
            """
            UPDATE gateways
            SET last_seen_at = :lastSeenAt, updated_at = :updatedAt
            WHERE id = :id
            """)
        .bind("lastSeenAt", now)
        .bind("updatedAt", now)
        .bind("id", gatewayId)
        .fetch()
        .rowsUpdated()
        .then();
  }

  public Mono<GatewayEntity> requireRegistered(String gatewayId) {
    GatewayIdValidator.validate(gatewayId);
    return gatewayRepository
        .findById(gatewayId)
        .switchIfEmpty(
            Mono.error(ControlPlaneException.gatewayNotRegistered("Gateway is not registered")));
  }

  private Mono<Json> serializeMetadata(Map<String, Object> metadata) {
    try {
      String json = objectMapper.writeValueAsString(metadata);
      if (json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_METADATA_BYTES) {
        return Mono.error(ControlPlaneException.invalidRequest("metadata exceeds size limit"));
      }
      return Mono.just(Json.of(json));
    } catch (JsonProcessingException e) {
      return Mono.error(ControlPlaneException.invalidRequest("metadata must be valid JSON"));
    }
  }

  public record RegistrationResult(
      String gatewayId,
      String gatewayGroup,
      String softwareVersion,
      OffsetDateTime registeredAt,
      OffsetDateTime lastSeenAt,
      boolean created) {

    static RegistrationResult from(GatewayEntity entity, boolean created) {
      return new RegistrationResult(
          entity.id(),
          entity.gatewayGroup(),
          entity.softwareVersion(),
          entity.registeredAt(),
          entity.lastSeenAt(),
          created);
    }
  }
}
