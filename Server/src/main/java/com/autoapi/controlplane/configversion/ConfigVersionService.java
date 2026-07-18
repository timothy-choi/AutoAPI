package com.autoapi.controlplane.configversion;

import com.autoapi.controlplane.DraftGraphService;
import com.autoapi.controlplane.api.ControlPlaneException;
import com.autoapi.controlplane.persistence.BackendHealthPolicyEntity;
import com.autoapi.controlplane.persistence.CircuitBreakerPolicyEntity;
import com.autoapi.controlplane.persistence.ConfigVersionEntity;
import com.autoapi.controlplane.persistence.ConfigVersionRepository;
import com.autoapi.controlplane.persistence.RateLimitPolicyEntity;
import com.autoapi.controlplane.persistence.RetryPolicyEntity;
import com.autoapi.controlplane.persistence.RoutePolicyBindingEntity;
import com.autoapi.controlplane.persistence.TrafficSplitDestinationEntity;
import com.autoapi.controlplane.persistence.TrafficSplitPolicyEntity;
import com.autoapi.controlplane.validation.DraftGraphValidator;
import com.autoapi.controlplane.validation.ValidationResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.r2dbc.postgresql.codec.Json;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@ConditionalOnProperty(
    name = "autoapi.controlplane.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class ConfigVersionService {

  private final DraftGraphService draftGraphService;
  private final ConfigVersionRepository configVersionRepository;
  private final DatabaseClient databaseClient;
  private final ObjectMapper objectMapper;

  public ConfigVersionService(
      DraftGraphService draftGraphService,
      ConfigVersionRepository configVersionRepository,
      DatabaseClient databaseClient,
      ObjectMapper objectMapper) {
    this.draftGraphService = draftGraphService;
    this.configVersionRepository = configVersionRepository;
    this.databaseClient = databaseClient;
    this.objectMapper = objectMapper;
  }

  public Mono<ValidationResult> validate(UUID apiId) {
    return draftGraphService
        .loadByApiId(apiId)
        .map(DraftGraphValidator::validate)
        .switchIfEmpty(
            Mono.just(
                com.autoapi.controlplane.validation.ValidationResult.invalid(
                    java.util.List.of(
                        new com.autoapi.controlplane.validation.ValidationError(
                            "API_NOT_FOUND", apiId, "API was not found")))));
  }

  @Transactional(transactionManager = "connectionFactoryTransactionManager")
  public Mono<ConfigVersionEntity> publish(UUID apiId, String message) {
    return draftGraphService
        .loadByApiId(apiId)
        .switchIfEmpty(Mono.error(ControlPlaneException.notFound("API was not found")))
        .flatMap(
            graph -> {
              ValidationResult validation = DraftGraphValidator.validate(graph);
              if (!validation.valid()) {
                return Mono.error(ControlPlaneException.validationFailed(validation.errors()));
              }
              return configVersionRepository
                  .findByApiIdAndContentHash(apiId, validation.contentHash())
                  .flatMap(
                      existing ->
                          Mono.<ConfigVersionEntity>error(
                              ControlPlaneException.configVersionAlreadyExists(existing)))
                  .switchIfEmpty(
                      Mono.defer(() -> lockApiAndInsert(apiId, validation, message, graph)));
            });
  }

  private Mono<ConfigVersionEntity> lockApiAndInsert(
      UUID apiId, ValidationResult validation, String message, DraftGraphService.DraftGraph graph) {
    return databaseClient
        .sql("SELECT id FROM apis WHERE id = $1 FOR UPDATE")
        .bind(0, apiId)
        .map(row -> row.get("id", UUID.class))
        .one()
        .switchIfEmpty(Mono.error(ControlPlaneException.notFound("API was not found")))
        .flatMap(
            ignored ->
                databaseClient
                    .sql(
                        "SELECT COALESCE(MAX(version), 0) + 1 AS next_version FROM config_versions WHERE api_id = $1")
                    .bind(0, apiId)
                    .map(row -> row.get("next_version", Long.class))
                    .one()
                    .flatMap(
                        nextVersion -> {
                          OffsetDateTime publishInstant = OffsetDateTime.now(ZoneOffset.UTC);
                          java.util.Map<UUID, RoutePolicyBindingEntity> bindingByRouteId =
                              graph.routePolicyBindings().stream()
                                  .collect(
                                      java.util.stream.Collectors.toMap(
                                          RoutePolicyBindingEntity::routeId, b -> b));
                          java.util.Map<UUID, RateLimitPolicyEntity> policyById =
                              graph.rateLimitPolicies().stream()
                                  .collect(
                                      java.util.stream.Collectors.toMap(
                                          RateLimitPolicyEntity::id, p -> p));
                          java.util.Map<UUID, BackendHealthPolicyEntity> healthPolicyById =
                              graph.backendHealthPolicies().stream()
                                  .collect(
                                      java.util.stream.Collectors.toMap(
                                          BackendHealthPolicyEntity::id, p -> p));
                          java.util.Map<UUID, RetryPolicyEntity> retryPolicyById =
                              graph.retryPolicies().stream()
                                  .collect(
                                      java.util.stream.Collectors.toMap(
                                          RetryPolicyEntity::id, p -> p));
                          java.util.Map<UUID, CircuitBreakerPolicyEntity> circuitBreakerPolicyById =
                              graph.circuitBreakerPolicies().stream()
                                  .collect(
                                      java.util.stream.Collectors.toMap(
                                          CircuitBreakerPolicyEntity::id, p -> p));
                          java.util.Map<UUID, TrafficSplitPolicyEntity> trafficSplitPolicyById =
                              graph.trafficSplitPolicies().stream()
                                  .collect(
                                      java.util.stream.Collectors.toMap(
                                          TrafficSplitPolicyEntity::id, p -> p));
                          java.util.Map<UUID, java.util.List<TrafficSplitDestinationEntity>>
                              destinationsByPolicyId =
                                  graph.trafficSplitDestinations().stream()
                                      .collect(
                                          java.util.stream.Collectors.groupingBy(
                                              TrafficSplitDestinationEntity::trafficSplitPolicyId));
                          HashableRuntimePayload payload =
                              RuntimeConfigCompiler.compile(
                                  apiId,
                                  graph.gatewayDefaults(),
                                  graph.routes().stream()
                                      .filter(
                                          com.autoapi.controlplane.persistence.RouteEntity::enabled)
                                      .toList(),
                                  graph.pools().stream()
                                      .collect(
                                          java.util.stream.Collectors.toMap(
                                              com.autoapi.controlplane.persistence
                                                      .UpstreamPoolEntity
                                                  ::id,
                                              p -> p)),
                                  graph.targets().stream()
                                      .collect(
                                          java.util.stream.Collectors.groupingBy(
                                              com.autoapi.controlplane.persistence
                                                      .UpstreamTargetEntity
                                                  ::upstreamPoolId)),
                                  bindingByRouteId,
                                  policyById,
                                  healthPolicyById,
                                  retryPolicyById,
                                  circuitBreakerPolicyById,
                                  trafficSplitPolicyById,
                                  destinationsByPolicyId,
                                  graph.apiKeys(),
                                  publishInstant);
                          StoredRuntimeSnapshot snapshot =
                              RuntimeConfigCompiler.toStoredSnapshot(
                                  payload, nextVersion, validation.contentHash(), publishInstant);
                          Json snapshotJson;
                          try {
                            snapshotJson =
                                Json.of(
                                    RuntimeContentHasher.canonicalMapper()
                                        .writeValueAsString(snapshot));
                          } catch (JsonProcessingException e) {
                            return Mono.error(
                                ControlPlaneException.internal("Failed to serialize snapshot"));
                          }
                          ConfigVersionEntity entity =
                              new ConfigVersionEntity(
                                  UUID.randomUUID(),
                                  apiId,
                                  nextVersion,
                                  validation.contentHash(),
                                  snapshotJson,
                                  message,
                                  OffsetDateTime.now(ZoneOffset.UTC));
                          return configVersionRepository
                              .save(entity)
                              .onErrorResume(
                                  DataIntegrityViolationException.class,
                                  ex ->
                                      configVersionRepository
                                          .findByApiIdAndContentHash(
                                              apiId, validation.contentHash())
                                          .flatMap(
                                              existing ->
                                                  Mono.<ConfigVersionEntity>error(
                                                      ControlPlaneException
                                                          .configVersionAlreadyExists(existing)))
                                          .switchIfEmpty(
                                              Mono.error(
                                                  ControlPlaneException.configVersionAlreadyExists(
                                                      null))));
                        }));
  }

  public Flux<ConfigVersionEntity> listMetadata(UUID apiId) {
    return configVersionRepository.findByApiIdOrderByVersionDesc(apiId);
  }

  public Mono<ConfigVersionEntity> get(UUID apiId, long version) {
    return configVersionRepository
        .findByApiIdAndVersion(apiId, version)
        .switchIfEmpty(Mono.error(ControlPlaneException.notFound("Config version was not found")));
  }
}
