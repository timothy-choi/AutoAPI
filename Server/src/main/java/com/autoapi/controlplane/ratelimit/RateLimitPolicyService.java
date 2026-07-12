package com.autoapi.controlplane.ratelimit;

import com.autoapi.controlplane.api.ControlPlaneException;
import com.autoapi.controlplane.apidefinition.ApiDefinitionService;
import com.autoapi.controlplane.persistence.RateLimitPolicyEntity;
import com.autoapi.controlplane.persistence.RateLimitPolicyRepository;
import com.autoapi.controlplane.persistence.RateLimitPolicyRepositoryCustom;
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
public class RateLimitPolicyService {

  public static final int MAX_LIMIT_COUNT = 10_000_000;
  public static final int MAX_WINDOW_SECONDS = 86_400;

  private final RateLimitPolicyRepository policyRepository;
  private final RateLimitPolicyRepositoryCustom policyRepositoryCustom;
  private final ApiDefinitionService apiDefinitionService;

  public RateLimitPolicyService(
      RateLimitPolicyRepository policyRepository,
      RateLimitPolicyRepositoryCustom policyRepositoryCustom,
      ApiDefinitionService apiDefinitionService) {
    this.policyRepository = policyRepository;
    this.policyRepositoryCustom = policyRepositoryCustom;
    this.apiDefinitionService = apiDefinitionService;
  }

  public Mono<RateLimitPolicyEntity> create(
      UUID apiId,
      String name,
      int limitCount,
      int windowSeconds,
      String identitySource,
      String redisFailureMode) {
    validateFields(limitCount, windowSeconds, identitySource, redisFailureMode);
    if (name == null || name.isBlank()) {
      return Mono.error(ControlPlaneException.invalidRequest("name is required"));
    }
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    RateLimitPolicyEntity entity =
        new RateLimitPolicyEntity(
            UUID.randomUUID(),
            apiId,
            name.trim(),
            limitCount,
            windowSeconds,
            identitySource,
            redisFailureMode,
            true,
            now,
            now);
    return apiDefinitionService
        .get(apiId)
        .then(policyRepository.save(entity))
        .onErrorResume(
            DataIntegrityViolationException.class,
            ex ->
                Mono.error(
                    ControlPlaneException.conflict("A rate limit policy with that name exists")));
  }

  public Flux<RateLimitPolicyEntity> list(UUID apiId) {
    return apiDefinitionService.get(apiId).thenMany(policyRepository.findByApiId(apiId));
  }

  public Mono<RateLimitPolicyEntity> get(UUID apiId, UUID policyId) {
    return apiDefinitionService
        .get(apiId)
        .then(policyRepository.findById(policyId))
        .switchIfEmpty(
            Mono.error(ControlPlaneException.notFound("Rate limit policy was not found")))
        .flatMap(
            policy ->
                policy.apiId().equals(apiId)
                    ? Mono.just(policy)
                    : Mono.error(
                        ControlPlaneException.notFound("Rate limit policy was not found")));
  }

  public Mono<RateLimitPolicyEntity> patch(
      UUID apiId,
      UUID policyId,
      Integer limitCount,
      Integer windowSeconds,
      String identitySource,
      String redisFailureMode,
      Boolean enabled) {
    return get(apiId, policyId)
        .flatMap(
            existing -> {
              int nextLimit = limitCount == null ? existing.limitCount() : limitCount;
              int nextWindow = windowSeconds == null ? existing.windowSeconds() : windowSeconds;
              String nextIdentity =
                  identitySource == null ? existing.identitySource() : identitySource;
              String nextFailureMode =
                  redisFailureMode == null ? existing.redisFailureMode() : redisFailureMode;
              boolean nextEnabled = enabled == null ? existing.enabled() : enabled;
              validateFields(nextLimit, nextWindow, nextIdentity, nextFailureMode);
              OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
              return policyRepositoryCustom.update(
                  existing.id(),
                  nextLimit,
                  nextWindow,
                  nextIdentity,
                  nextFailureMode,
                  nextEnabled,
                  now);
            });
  }

  public static void validateFields(
      int limitCount, int windowSeconds, String identitySource, String redisFailureMode) {
    if (limitCount <= 0 || limitCount > MAX_LIMIT_COUNT) {
      throw ControlPlaneException.invalidRequest(
          "limitCount must be between 1 and " + MAX_LIMIT_COUNT);
    }
    if (windowSeconds <= 0 || windowSeconds > MAX_WINDOW_SECONDS) {
      throw ControlPlaneException.invalidRequest(
          "windowSeconds must be between 1 and " + MAX_WINDOW_SECONDS);
    }
    if (!"API_KEY".equals(identitySource)) {
      throw ControlPlaneException.invalidRequest("identitySource must be API_KEY");
    }
    if (!"FAIL_OPEN".equals(redisFailureMode) && !"FAIL_CLOSED".equals(redisFailureMode)) {
      throw ControlPlaneException.invalidRequest(
          "redisFailureMode must be FAIL_OPEN or FAIL_CLOSED");
    }
  }
}
