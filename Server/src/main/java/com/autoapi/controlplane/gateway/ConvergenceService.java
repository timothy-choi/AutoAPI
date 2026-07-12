package com.autoapi.controlplane.gateway;

import com.autoapi.controlplane.ControlPlaneProperties;
import com.autoapi.controlplane.apidefinition.ApiDefinitionService;
import com.autoapi.controlplane.gateway.ConvergenceService.ConvergenceResponse;
import com.autoapi.controlplane.gateway.ConvergenceService.ConvergenceState;
import com.autoapi.controlplane.gateway.ConvergenceService.GatewayConvergenceEntry;
import com.autoapi.controlplane.persistence.ConfigVersionRepository;
import com.autoapi.controlplane.persistence.GatewayApiStatusEntity;
import com.autoapi.controlplane.persistence.GatewayApiStatusRepository;
import com.autoapi.controlplane.persistence.GatewayEntity;
import com.autoapi.controlplane.persistence.GatewayRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@ConditionalOnProperty(
    name = "autoapi.controlplane.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class ConvergenceService {

  private final ApiDefinitionService apiDefinitionService;
  private final ConfigVersionRepository configVersionRepository;
  private final GatewayRepository gatewayRepository;
  private final GatewayApiStatusRepository gatewayApiStatusRepository;
  private final ControlPlaneProperties controlPlaneProperties;

  public ConvergenceService(
      ApiDefinitionService apiDefinitionService,
      ConfigVersionRepository configVersionRepository,
      GatewayRepository gatewayRepository,
      GatewayApiStatusRepository gatewayApiStatusRepository,
      ControlPlaneProperties controlPlaneProperties) {
    this.apiDefinitionService = apiDefinitionService;
    this.configVersionRepository = configVersionRepository;
    this.gatewayRepository = gatewayRepository;
    this.gatewayApiStatusRepository = gatewayApiStatusRepository;
    this.controlPlaneProperties = controlPlaneProperties;
  }

  public Mono<ConvergenceResponse> getConvergence(UUID apiId) {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    OffsetDateTime staleCutoff = now.minus(controlPlaneProperties.gatewayStaleAfter());

    return apiDefinitionService
        .get(apiId)
        .flatMap(
            api -> {
              if (!api.enabled()) {
                return Mono.just(ConvergenceResponse.disabled(apiId, api.desiredConfigVersion()));
              }
              Long desiredVersion = api.desiredConfigVersion();
              if (desiredVersion == null) {
                return Mono.just(ConvergenceResponse.unpublished(apiId));
              }
              return configVersionRepository
                  .findByApiIdAndVersion(apiId, desiredVersion)
                  .flatMap(
                      desiredConfig ->
                          gatewayRepository
                              .findAll()
                              .collectList()
                              .zipWith(gatewayApiStatusRepository.findByApiId(apiId).collectList())
                              .map(
                                  tuple -> {
                                    List<GatewayEntity> gateways = tuple.getT1();
                                    Map<String, GatewayApiStatusEntity> statusByGateway =
                                        tuple.getT2().stream()
                                            .collect(
                                                Collectors.toMap(
                                                    GatewayApiStatusEntity::gatewayId, s -> s));

                                    List<GatewayConvergenceEntry> entries = new ArrayList<>();
                                    int liveCount = 0;
                                    int staleCount = 0;
                                    int ackedCount = 0;
                                    int nackedCount = 0;
                                    int pendingCount = 0;

                                    for (GatewayEntity gateway : gateways) {
                                      boolean live =
                                          gateway.lastSeenAt() != null
                                              && !gateway.lastSeenAt().isBefore(staleCutoff);
                                      if (live) {
                                        liveCount++;
                                      } else {
                                        staleCount++;
                                      }
                                      GatewayApiStatusEntity status =
                                          statusByGateway.get(gateway.id());
                                      entries.add(
                                          GatewayConvergenceEntry.from(
                                              gateway, status, live, desiredVersion));

                                      if (!live) {
                                        continue;
                                      }
                                      if (status == null) {
                                        pendingCount++;
                                        continue;
                                      }
                                      if ("NACK".equals(status.lastStatus())
                                          && status.lastAttemptedVersion() != null
                                          && status.lastAttemptedVersion() == desiredVersion) {
                                        nackedCount++;
                                        continue;
                                      }
                                      if ("ACK".equals(status.lastStatus())
                                          && status.activeVersion() != null
                                          && status.activeVersion() == desiredVersion) {
                                        ackedCount++;
                                        continue;
                                      }
                                      if (status.activeVersion() != null
                                          && status.activeVersion() != desiredVersion
                                          && status.lastAttemptedVersion() != null
                                          && status.lastAttemptedVersion() == desiredVersion) {
                                        nackedCount++;
                                        continue;
                                      }
                                      pendingCount++;
                                    }

                                    entries.sort(
                                        Comparator.comparing(GatewayConvergenceEntry::gatewayId));

                                    ConvergenceState state =
                                        deriveState(
                                            liveCount, ackedCount, nackedCount, pendingCount);

                                    return new ConvergenceResponse(
                                        apiId,
                                        desiredVersion,
                                        desiredConfig.contentHash(),
                                        state.name(),
                                        liveCount,
                                        staleCount,
                                        ackedCount,
                                        nackedCount,
                                        pendingCount,
                                        entries);
                                  }));
            });
  }

  private static ConvergenceState deriveState(
      int liveCount, int ackedCount, int nackedCount, int pendingCount) {
    if (liveCount == 0) {
      return ConvergenceState.CONVERGING;
    }
    if (nackedCount > 0) {
      return ConvergenceState.DEGRADED;
    }
    if (pendingCount > 0) {
      return ConvergenceState.CONVERGING;
    }
    if (ackedCount == liveCount) {
      return ConvergenceState.CONVERGED;
    }
    return ConvergenceState.DEGRADED;
  }

  public enum ConvergenceState {
    UNPUBLISHED,
    CONVERGING,
    CONVERGED,
    DEGRADED,
    DISABLED
  }

  public record ConvergenceResponse(
      UUID apiId,
      Long desiredVersion,
      String desiredContentHash,
      String derivedState,
      int liveGatewayCount,
      int staleGatewayCount,
      int ackedGatewayCount,
      int nackedGatewayCount,
      int pendingGatewayCount,
      List<GatewayConvergenceEntry> gateways) {

    static ConvergenceResponse disabled(UUID apiId, Long desiredVersion) {
      return new ConvergenceResponse(
          apiId, desiredVersion, null, ConvergenceState.DISABLED.name(), 0, 0, 0, 0, 0, List.of());
    }

    static ConvergenceResponse unpublished(UUID apiId) {
      return new ConvergenceResponse(
          apiId, null, null, ConvergenceState.UNPUBLISHED.name(), 0, 0, 0, 0, 0, List.of());
    }
  }

  public record GatewayConvergenceEntry(
      String gatewayId,
      String gatewayGroup,
      String liveness,
      Long activeVersion,
      Long lastAttemptedVersion,
      String lastStatus,
      String lastErrorCode,
      String lastSeenAt) {

    static GatewayConvergenceEntry from(
        GatewayEntity gateway, GatewayApiStatusEntity status, boolean live, long desiredVersion) {
      return new GatewayConvergenceEntry(
          gateway.id(),
          gateway.gatewayGroup(),
          live ? "LIVE" : "STALE",
          status == null ? null : status.activeVersion(),
          status == null ? null : status.lastAttemptedVersion(),
          status == null ? "REGISTERED" : status.lastStatus(),
          status == null ? null : status.lastErrorCode(),
          gateway.lastSeenAt().toString());
    }
  }
}
