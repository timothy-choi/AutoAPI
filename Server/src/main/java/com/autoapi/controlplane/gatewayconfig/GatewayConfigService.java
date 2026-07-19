package com.autoapi.controlplane.gatewayconfig;

import com.autoapi.controlplane.api.ControlPlaneException;
import com.autoapi.controlplane.configversion.RuntimeContentHasher;
import com.autoapi.controlplane.configversion.StoredRuntimeSnapshot;
import com.autoapi.controlplane.persistence.ApiRepository;
import com.autoapi.controlplane.persistence.ConfigVersionEntity;
import com.autoapi.controlplane.persistence.ConfigVersionRepository;
import com.autoapi.controlplane.rollout.EffectiveDesiredConfig;
import com.autoapi.controlplane.rollout.EffectiveDesiredConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@ConditionalOnProperty(
    name = "autoapi.controlplane.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class GatewayConfigService {

  private final ApiRepository apiRepository;
  private final ConfigVersionRepository configVersionRepository;
  private final ObjectMapper objectMapper;
  private final ObjectProvider<EffectiveDesiredConfigService> effectiveDesiredConfigService;

  public GatewayConfigService(
      ApiRepository apiRepository,
      ConfigVersionRepository configVersionRepository,
      ObjectMapper objectMapper,
      ObjectProvider<EffectiveDesiredConfigService> effectiveDesiredConfigService) {
    this.apiRepository = apiRepository;
    this.configVersionRepository = configVersionRepository;
    this.objectMapper = objectMapper;
    this.effectiveDesiredConfigService = effectiveDesiredConfigService;
  }

  public Mono<DesiredConfigMetadata> getDesiredMetadata(UUID apiId) {
    return getDesiredMetadata(apiId, null);
  }

  public Mono<DesiredConfigMetadata> getDesiredMetadata(UUID apiId, String gatewayId) {
    if (gatewayId != null && !gatewayId.isBlank()) {
      EffectiveDesiredConfigService resolver = effectiveDesiredConfigService.getIfAvailable();
      if (resolver != null) {
        return resolver.resolve(gatewayId, apiId).map(GatewayConfigService::fromEffectiveDesired);
      }
    }
    return apiRepository
        .findById(apiId)
        .switchIfEmpty(Mono.error(ControlPlaneException.notFound("API was not found")))
        .flatMap(
            api -> {
              Long desiredVersion = api.desiredConfigVersion();
              if (desiredVersion == null) {
                return Mono.error(
                    ControlPlaneException.desiredConfigNotSet(
                        "No desired configuration version has been activated"));
              }
              return configVersionRepository
                  .findByApiIdAndVersion(apiId, desiredVersion)
                  .switchIfEmpty(
                      Mono.error(
                          ControlPlaneException.notFound(
                              "Activated config version metadata was not found")))
                  .map(
                      entity ->
                          new DesiredConfigMetadata(
                              apiId,
                              entity.version(),
                              entity.contentHash(),
                              snapshotPath(apiId, entity.version())));
            });
  }

  public Mono<ConfigVersionEntity> getSnapshotEntity(UUID apiId, long version) {
    return apiRepository
        .findById(apiId)
        .switchIfEmpty(Mono.error(ControlPlaneException.notFound("API was not found")))
        .flatMap(
            ignored ->
                configVersionRepository
                    .findByApiIdAndVersion(apiId, version)
                    .switchIfEmpty(
                        Mono.error(
                            ControlPlaneException.configVersionNotFound(
                                "Config version was not found"))));
  }

  public Mono<StoredRuntimeSnapshot> readSnapshot(ConfigVersionEntity entity) {
    try {
      StoredRuntimeSnapshot snapshot =
          RuntimeContentHasher.canonicalMapper()
              .readValue(entity.configSnapshot().asString(), StoredRuntimeSnapshot.class);
      if (!entity.apiId().equals(snapshot.apiId()) || entity.version() != snapshot.version()) {
        return Mono.error(
            ControlPlaneException.internal("Stored snapshot metadata does not match version row"));
      }
      return Mono.just(snapshot);
    } catch (Exception e) {
      return Mono.error(ControlPlaneException.internal("Failed to read stored snapshot"));
    }
  }

  public static String snapshotPath(UUID apiId, long version) {
    return "/api/v1/gateway-config/" + apiId + "/versions/" + version;
  }

  private static DesiredConfigMetadata fromEffectiveDesired(EffectiveDesiredConfig effective) {
    return new DesiredConfigMetadata(
        effective.apiId(),
        effective.version(),
        effective.contentHash(),
        effective.snapshotUrl(),
        effective.rolloutId(),
        effective.rolloutStageIndex(),
        effective.assignmentGeneration(),
        effective.source().name());
  }

  public record DesiredConfigMetadata(
      UUID apiId,
      long version,
      String contentHash,
      String snapshotUrl,
      UUID rolloutId,
      Integer rolloutStageIndex,
      Long assignmentGeneration,
      String desiredSource) {

    public DesiredConfigMetadata(UUID apiId, long version, String contentHash, String snapshotUrl) {
      this(apiId, version, contentHash, snapshotUrl, null, null, null, "API_DEFAULT");
    }

    public String etagToken() {
      if (rolloutId != null && assignmentGeneration != null) {
        return contentHash + ":" + rolloutId + ":" + assignmentGeneration;
      }
      return contentHash;
    }
  }
}
