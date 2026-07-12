package com.autoapi.controlplane.backendhealth;

import com.autoapi.controlplane.api.ControlPlaneException;
import com.autoapi.controlplane.apidefinition.ApiDefinitionService;
import com.autoapi.controlplane.persistence.BackendHealthPolicyEntity;
import com.autoapi.controlplane.persistence.BackendHealthPolicyRepository;
import com.autoapi.controlplane.persistence.BackendHealthPolicyRepositoryCustom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
public class BackendHealthPolicyService {

  public static final int MAX_THRESHOLD = 100;
  public static final int MAX_EJECTION_SECONDS = 3600;

  private final BackendHealthPolicyRepository policyRepository;
  private final BackendHealthPolicyRepositoryCustom policyRepositoryCustom;
  private final ApiDefinitionService apiDefinitionService;

  public BackendHealthPolicyService(
      BackendHealthPolicyRepository policyRepository,
      BackendHealthPolicyRepositoryCustom policyRepositoryCustom,
      ApiDefinitionService apiDefinitionService) {
    this.policyRepository = policyRepository;
    this.policyRepositoryCustom = policyRepositoryCustom;
    this.apiDefinitionService = apiDefinitionService;
  }

  public Mono<BackendHealthPolicyEntity> create(
      UUID apiId,
      String name,
      int consecutiveFailureThreshold,
      int ejectionDurationSeconds,
      int maxEjectionPercent,
      boolean enabled) {
    validateFields(consecutiveFailureThreshold, ejectionDurationSeconds, maxEjectionPercent);
    if (name == null || name.isBlank()) {
      return Mono.error(ControlPlaneException.invalidRequest("name is required"));
    }
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    BackendHealthPolicyEntity entity =
        new BackendHealthPolicyEntity(
            UUID.randomUUID(),
            apiId,
            name.trim(),
            consecutiveFailureThreshold,
            ejectionDurationSeconds,
            maxEjectionPercent,
            enabled,
            now,
            now);
    return apiDefinitionService
        .get(apiId)
        .then(policyRepository.save(entity))
        .onErrorResume(
            DataIntegrityViolationException.class,
            ex ->
                Mono.error(
                    ControlPlaneException.conflict(
                        "A backend health policy with that name exists")));
  }

  public Flux<BackendHealthPolicyEntity> list(UUID apiId) {
    return apiDefinitionService.get(apiId).thenMany(policyRepository.findByApiId(apiId));
  }

  public Mono<BackendHealthPolicyEntity> get(UUID apiId, UUID policyId) {
    return apiDefinitionService
        .get(apiId)
        .then(policyRepository.findById(policyId))
        .switchIfEmpty(
            Mono.error(ControlPlaneException.notFound("Backend health policy was not found")))
        .flatMap(
            policy ->
                policy.apiId().equals(apiId)
                    ? Mono.just(policy)
                    : Mono.error(
                        ControlPlaneException.notFound("Backend health policy was not found")));
  }

  public Mono<BackendHealthPolicyEntity> patch(
      UUID apiId,
      UUID policyId,
      Integer consecutiveFailureThreshold,
      Integer ejectionDurationSeconds,
      Integer maxEjectionPercent,
      Boolean enabled) {
    return get(apiId, policyId)
        .flatMap(
            existing -> {
              int nextThreshold =
                  consecutiveFailureThreshold == null
                      ? existing.consecutiveFailureThreshold()
                      : consecutiveFailureThreshold;
              int nextDuration =
                  ejectionDurationSeconds == null
                      ? existing.ejectionDurationSeconds()
                      : ejectionDurationSeconds;
              int nextMaxPercent =
                  maxEjectionPercent == null ? existing.maxEjectionPercent() : maxEjectionPercent;
              boolean nextEnabled = enabled == null ? existing.enabled() : enabled;
              validateFields(nextThreshold, nextDuration, nextMaxPercent);
              OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
              return policyRepositoryCustom.update(
                  existing.id(), nextThreshold, nextDuration, nextMaxPercent, nextEnabled, now);
            });
  }

  public static void validateFields(
      int consecutiveFailureThreshold, int ejectionDurationSeconds, int maxEjectionPercent) {
    if (consecutiveFailureThreshold < 1 || consecutiveFailureThreshold > MAX_THRESHOLD) {
      throw ControlPlaneException.invalidRequest(
          "consecutiveFailureThreshold must be between 1 and " + MAX_THRESHOLD);
    }
    if (ejectionDurationSeconds < 1 || ejectionDurationSeconds > MAX_EJECTION_SECONDS) {
      throw ControlPlaneException.invalidRequest(
          "ejectionDurationSeconds must be between 1 and " + MAX_EJECTION_SECONDS);
    }
    if (maxEjectionPercent < 0 || maxEjectionPercent > 100) {
      throw ControlPlaneException.invalidRequest("maxEjectionPercent must be between 0 and 100");
    }
  }
}
