package com.autoapi.controlplane.discovery;

import com.autoapi.controlplane.api.ControlPlaneException;
import com.autoapi.controlplane.persistence.DiscoveredServiceEntity;
import com.autoapi.controlplane.persistence.DiscoveredServiceRepository;
import com.autoapi.controlplane.persistence.DiscoveredServiceRepositoryCustom;
import com.autoapi.controlplane.project.ProjectService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@ConditionalOnProperty(
    name = "autoapi.controlplane.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class DiscoveredServiceService {

  private static final Set<String> SELECTION_STRATEGIES = Set.of("ROUND_ROBIN", "CONSISTENT_HASH");
  private static final Set<String> REGISTRATION_MODES = Set.of("OPEN", "CREDENTIAL_REQUIRED");
  private static final Set<String> CONSISTENT_HASH_KEYS =
      Set.of("REQUEST_ID", "API_KEY_ID", "HEADER");

  private final ProjectService projectService;
  private final DiscoveredServiceRepository repository;
  private final DiscoveredServiceRepositoryCustom repositoryCustom;

  public DiscoveredServiceService(
      ProjectService projectService,
      DiscoveredServiceRepository repository,
      DiscoveredServiceRepositoryCustom repositoryCustom) {
    this.projectService = projectService;
    this.repository = repository;
    this.repositoryCustom = repositoryCustom;
  }

  public Mono<DiscoveredServiceEntity> create(
      UUID projectId,
      String name,
      String description,
      String selectionStrategy,
      String registrationMode,
      String defaultScheme,
      Integer defaultPort,
      String consistentHashKey,
      String consistentHashKeyName,
      Map<String, String> metadata,
      boolean enabled) {
    validateName(name);
    String strategy = normalizeSelectionStrategy(selectionStrategy);
    String mode = normalizeRegistrationMode(registrationMode);
    String scheme = normalizeScheme(defaultScheme);
    int port = defaultPort == null ? 8080 : defaultPort;
    ServiceInstanceValidation.validatePort(port);
    validateConsistentHashKey(strategy, consistentHashKey, consistentHashKeyName);
    String metadataJson = ServiceMetadataValidator.normalizeOrEmpty(metadata);
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    return projectService
        .get(projectId)
        .flatMap(
            ignored -> {
              DiscoveredServiceEntity entity =
                  new DiscoveredServiceEntity(
                      UUID.randomUUID(),
                      projectId,
                      name.trim(),
                      description,
                      enabled,
                      strategy,
                      mode,
                      scheme,
                      port,
                      normalizeConsistentHashKey(consistentHashKey),
                      consistentHashKeyName,
                      0L,
                      metadataJson,
                      now,
                      now);
              return repository
                  .save(entity)
                  .onErrorMap(
                      DataIntegrityViolationException.class,
                      ex ->
                          ControlPlaneException.conflict(
                              "A service with the same name already exists for project"));
            });
  }

  public Flux<DiscoveredServiceEntity> list(UUID projectId) {
    return projectService.get(projectId).thenMany(repository.findByProjectId(projectId));
  }

  public Mono<DiscoveredServiceEntity> get(UUID projectId, UUID serviceId) {
    return projectService
        .get(projectId)
        .then(
            repository
                .findByProjectIdAndId(projectId, serviceId)
                .switchIfEmpty(
                    Mono.error(ControlPlaneException.notFound("Service was not found"))));
  }

  public Mono<DiscoveredServiceEntity> getById(UUID serviceId) {
    return repository
        .findById(serviceId)
        .switchIfEmpty(Mono.error(ControlPlaneException.notFound("Service was not found")));
  }

  public Mono<DiscoveredServiceEntity> patch(
      UUID projectId,
      UUID serviceId,
      String name,
      String description,
      Boolean enabled,
      String selectionStrategy,
      String registrationMode,
      String defaultScheme,
      Integer defaultPort,
      String consistentHashKey,
      String consistentHashKeyName,
      Map<String, String> metadata) {
    return get(projectId, serviceId)
        .flatMap(
            existing -> {
              if (name != null) {
                validateName(name);
              }
              String strategy =
                  selectionStrategy == null ? null : normalizeSelectionStrategy(selectionStrategy);
              String mode =
                  registrationMode == null ? null : normalizeRegistrationMode(registrationMode);
              if (defaultPort != null) {
                ServiceInstanceValidation.validatePort(defaultPort);
              }
              String metadataJson =
                  metadata == null ? null : ServiceMetadataValidator.normalizeOrEmpty(metadata);
              String effectiveStrategy = strategy == null ? existing.selectionStrategy() : strategy;
              validateConsistentHashKey(
                  effectiveStrategy,
                  consistentHashKey == null ? existing.consistentHashKey() : consistentHashKey,
                  consistentHashKeyName == null
                      ? existing.consistentHashKeyName()
                      : consistentHashKeyName);
              OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
              return repositoryCustom.patch(
                  serviceId,
                  name == null ? null : name.trim(),
                  description,
                  enabled,
                  strategy,
                  mode,
                  defaultScheme == null ? null : normalizeScheme(defaultScheme),
                  defaultPort,
                  consistentHashKey == null ? null : normalizeConsistentHashKey(consistentHashKey),
                  consistentHashKeyName,
                  metadataJson,
                  now);
            });
  }

  public Mono<Void> delete(UUID projectId, UUID serviceId) {
    return get(projectId, serviceId).flatMap(existing -> repository.deleteById(serviceId));
  }

  public Mono<DiscoveredServiceEntity> incrementMembershipVersion(UUID serviceId) {
    return repositoryCustom.incrementMembershipVersion(
        serviceId, OffsetDateTime.now(ZoneOffset.UTC));
  }

  private static void validateName(String name) {
    if (name == null || name.isBlank()) {
      throw ControlPlaneException.invalidRequest("name is required");
    }
    if (name.length() > 255) {
      throw ControlPlaneException.invalidRequest("name exceeds max length");
    }
  }

  private static String normalizeSelectionStrategy(String selectionStrategy) {
    String normalized =
        selectionStrategy == null || selectionStrategy.isBlank()
            ? "ROUND_ROBIN"
            : selectionStrategy.toUpperCase(Locale.ROOT);
    if (!SELECTION_STRATEGIES.contains(normalized)) {
      throw ControlPlaneException.invalidRequest("Unsupported selection strategy: " + normalized);
    }
    return normalized;
  }

  private static String normalizeRegistrationMode(String registrationMode) {
    String normalized =
        registrationMode == null || registrationMode.isBlank()
            ? "OPEN"
            : registrationMode.toUpperCase(Locale.ROOT);
    if (!REGISTRATION_MODES.contains(normalized)) {
      throw ControlPlaneException.invalidRequest("Unsupported registration mode: " + normalized);
    }
    return normalized;
  }

  private static String normalizeScheme(String scheme) {
    return scheme == null || scheme.isBlank() ? "http" : scheme.toLowerCase(Locale.ROOT);
  }

  private static String normalizeConsistentHashKey(String consistentHashKey) {
    return consistentHashKey == null || consistentHashKey.isBlank()
        ? "REQUEST_ID"
        : consistentHashKey.toUpperCase(Locale.ROOT);
  }

  private static void validateConsistentHashKey(
      String selectionStrategy, String consistentHashKey, String consistentHashKeyName) {
    if (!"CONSISTENT_HASH".equals(selectionStrategy)) {
      return;
    }
    String key = normalizeConsistentHashKey(consistentHashKey);
    if (!CONSISTENT_HASH_KEYS.contains(key)) {
      throw ControlPlaneException.invalidRequest("Unsupported consistent hash key: " + key);
    }
    if ("HEADER".equals(key)
        && (consistentHashKeyName == null || consistentHashKeyName.isBlank())) {
      throw ControlPlaneException.invalidRequest(
          "consistentHashKeyName is required when consistentHashKey is HEADER");
    }
    if (!"HEADER".equals(key) && consistentHashKeyName != null) {
      throw ControlPlaneException.invalidRequest(
          "consistentHashKeyName is only allowed when consistentHashKey is HEADER");
    }
  }
}
