package com.autoapi.controlplane.gateway;

import com.autoapi.controlplane.api.ControlPlaneException;
import com.autoapi.controlplane.apidefinition.ApiDefinitionService;
import com.autoapi.controlplane.gateway.GatewayHeartbeatService.HeartbeatResult;
import com.autoapi.controlplane.persistence.ConfigVersionRepository;
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
public class GatewayHeartbeatService {

  private final GatewayRegistrationService registrationService;
  private final ApiDefinitionService apiDefinitionService;
  private final ConfigVersionRepository configVersionRepository;
  private final DatabaseClient databaseClient;

  public GatewayHeartbeatService(
      GatewayRegistrationService registrationService,
      ApiDefinitionService apiDefinitionService,
      ConfigVersionRepository configVersionRepository,
      DatabaseClient databaseClient) {
    this.registrationService = registrationService;
    this.apiDefinitionService = apiDefinitionService;
    this.configVersionRepository = configVersionRepository;
    this.databaseClient = databaseClient;
  }

  public Mono<HeartbeatResult> heartbeat(
      String gatewayId, OffsetDateTime sentAt, List<ApiStatusSummary> apiStatuses) {
    if (sentAt == null) {
      return Mono.error(ControlPlaneException.invalidHeartbeat("sentAt is required"));
    }
    return registrationService
        .requireRegistered(gatewayId)
        .flatMap(
            ignored ->
                validateApiStatuses(apiStatuses)
                    .then(registrationService.touchLastSeen(gatewayId))
                    .then(updateApiStatusSummaries(gatewayId, apiStatuses))
                    .thenReturn(
                        new HeartbeatResult(gatewayId, OffsetDateTime.now(ZoneOffset.UTC), 10)));
  }

  private Mono<Void> validateApiStatuses(List<ApiStatusSummary> apiStatuses) {
    if (apiStatuses == null || apiStatuses.isEmpty()) {
      return Mono.empty();
    }
    return Flux.fromIterable(apiStatuses)
        .concatMap(
            summary ->
                apiDefinitionService
                    .get(summary.apiId())
                    .then(
                        configVersionRepository
                            .findByApiIdAndVersion(summary.apiId(), summary.activeVersion())
                            .switchIfEmpty(
                                Mono.error(
                                    ControlPlaneException.invalidHeartbeat(
                                        "Active version does not exist for API")))
                            .flatMap(
                                version ->
                                    version.contentHash().equals(summary.activeContentHash())
                                        ? Mono.empty()
                                        : Mono.error(
                                            ControlPlaneException.invalidHeartbeat(
                                                "Active content hash does not match version")))))
        .then();
  }

  private Mono<Void> updateApiStatusSummaries(
      String gatewayId, List<ApiStatusSummary> apiStatuses) {
    if (apiStatuses == null || apiStatuses.isEmpty()) {
      return Mono.empty();
    }
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    return Flux.fromIterable(apiStatuses)
        .concatMap(
            summary ->
                databaseClient
                    .sql(
                        """
                        INSERT INTO gateway_api_status (
                            gateway_id, api_id, active_version, active_content_hash,
                            last_attempted_version, last_attempted_content_hash,
                            last_status, last_reported_at, created_at, updated_at
                        ) VALUES (
                            :gatewayId, :apiId, :activeVersion, :activeContentHash,
                            :activeVersion, :activeContentHash,
                            'REGISTERED', :now, :now, :now
                        )
                        ON CONFLICT (gateway_id, api_id) DO UPDATE SET
                            active_version = EXCLUDED.active_version,
                            active_content_hash = EXCLUDED.active_content_hash,
                            updated_at = EXCLUDED.updated_at
                        """)
                    .bind("gatewayId", gatewayId)
                    .bind("apiId", summary.apiId())
                    .bind("activeVersion", summary.activeVersion())
                    .bind("activeContentHash", summary.activeContentHash())
                    .bind("now", now)
                    .fetch()
                    .rowsUpdated()
                    .then())
        .then();
  }

  public record ApiStatusSummary(UUID apiId, long activeVersion, String activeContentHash) {}

  public record HeartbeatResult(
      String gatewayId, OffsetDateTime receivedAt, int nextHeartbeatSeconds) {}
}
