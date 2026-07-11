package com.autoapi.controlplane.activation;

import com.autoapi.controlplane.api.ControlPlaneException;
import com.autoapi.controlplane.persistence.ApiEntity;
import com.autoapi.controlplane.persistence.ApiRepository;
import com.autoapi.controlplane.persistence.ConfigVersionRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@ConditionalOnProperty(
    name = "autoapi.controlplane.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class ConfigActivationService {

  private final ApiRepository apiRepository;
  private final ConfigVersionRepository configVersionRepository;
  private final DatabaseClient databaseClient;

  public ConfigActivationService(
      ApiRepository apiRepository,
      ConfigVersionRepository configVersionRepository,
      DatabaseClient databaseClient) {
    this.apiRepository = apiRepository;
    this.configVersionRepository = configVersionRepository;
    this.databaseClient = databaseClient;
  }

  public Mono<ActivationResult> activate(UUID apiId, long version, Long expectedDesiredVersion) {
    return apiRepository
        .findById(apiId)
        .switchIfEmpty(Mono.error(ControlPlaneException.notFound("API was not found")))
        .flatMap(
            api ->
                configVersionRepository
                    .findByApiIdAndVersion(apiId, version)
                    .switchIfEmpty(
                        Mono.error(
                            ControlPlaneException.configVersionNotFound(
                                "Config version was not found")))
                    .flatMap(
                        configVersion ->
                            ensureActivatable(api, configVersion.contentHash())
                                .then(conditionalUpdate(apiId, version, expectedDesiredVersion))
                                .flatMap(
                                    updated ->
                                        updated
                                            ? apiRepository
                                                .findById(apiId)
                                                .map(
                                                    refreshed ->
                                                        new ActivationResult(
                                                            apiId,
                                                            version,
                                                            configVersion.contentHash(),
                                                            refreshed.updatedAt()))
                                            : Mono.error(
                                                ControlPlaneException.desiredVersionConflict(
                                                    "Desired configuration version changed"
                                                        + " concurrently")))));
  }

  private Mono<Void> ensureActivatable(ApiEntity api, String contentHash) {
    if (!api.enabled()) {
      return Mono.error(ControlPlaneException.configVersionNotActivatable("API is disabled"));
    }
    if (contentHash == null || contentHash.isBlank()) {
      return Mono.error(
          ControlPlaneException.configVersionNotActivatable(
              "Config version is missing content hash"));
    }
    return Mono.empty();
  }

  private Mono<Boolean> conditionalUpdate(
      UUID apiId, long newVersion, Long expectedDesiredVersion) {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    var spec =
        databaseClient
            .sql(
                """
                UPDATE apis
                SET desired_config_version = :newVersion,
                    updated_at = :updatedAt
                WHERE id = :apiId
                  AND (
                    (:expectedVersion IS NULL AND desired_config_version IS NULL)
                    OR desired_config_version = :expectedVersion
                  )
                """)
            .bind("newVersion", newVersion)
            .bind("updatedAt", now)
            .bind("apiId", apiId);
    if (expectedDesiredVersion == null) {
      spec = spec.bindNull("expectedVersion", Long.class);
    } else {
      spec = spec.bind("expectedVersion", expectedDesiredVersion);
    }
    return spec.fetch().rowsUpdated().map(rows -> rows != null && rows == 1L);
  }

  public record ActivationResult(
      UUID apiId, long desiredVersion, String contentHash, OffsetDateTime activatedAt) {}
}
