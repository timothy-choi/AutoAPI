package com.autoapi.controlplane.circuitbreaker;

import com.autoapi.controlplane.api.ControlPlaneException;
import com.autoapi.controlplane.apidefinition.ApiDefinitionService;
import com.autoapi.controlplane.persistence.CircuitBreakerPolicyEntity;
import com.autoapi.controlplane.persistence.CircuitBreakerPolicyRepository;
import com.autoapi.controlplane.persistence.CircuitBreakerPolicyRepositoryCustom;
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
public class CircuitBreakerPolicyService {

  public static final int MIN_FAILURE_THRESHOLD = 1;
  public static final int MAX_FAILURE_THRESHOLD = 1000;
  public static final int MIN_ROLLING_WINDOW_SECONDS = 1;
  public static final int MAX_ROLLING_WINDOW_SECONDS = 3600;
  public static final int MIN_OPEN_DURATION_SECONDS = 1;
  public static final int MAX_OPEN_DURATION_SECONDS = 3600;
  public static final int MIN_HALF_OPEN_MAX_REQUESTS = 1;
  public static final int MAX_HALF_OPEN_MAX_REQUESTS = 100;
  public static final int MIN_SUCCESS_THRESHOLD = 1;
  public static final int MAX_SUCCESS_THRESHOLD = 100;

  private final CircuitBreakerPolicyRepository policyRepository;
  private final CircuitBreakerPolicyRepositoryCustom policyRepositoryCustom;
  private final ApiDefinitionService apiDefinitionService;

  public CircuitBreakerPolicyService(
      CircuitBreakerPolicyRepository policyRepository,
      CircuitBreakerPolicyRepositoryCustom policyRepositoryCustom,
      ApiDefinitionService apiDefinitionService) {
    this.policyRepository = policyRepository;
    this.policyRepositoryCustom = policyRepositoryCustom;
    this.apiDefinitionService = apiDefinitionService;
  }

  public Mono<CircuitBreakerPolicyEntity> create(
      UUID apiId,
      String name,
      int failureThreshold,
      int rollingWindowSeconds,
      int openDurationSeconds,
      int halfOpenMaxRequests,
      int successThreshold,
      boolean predicateCountHttp5xx,
      boolean predicateCountConnectFailure,
      boolean predicateCountConnectTimeout,
      boolean predicateCountReadTimeout,
      boolean predicateCountTlsFailure,
      boolean predicateCountTransportException,
      boolean predicateCountHttp429,
      boolean enabled) {
    if (name == null || name.isBlank()) {
      return Mono.error(ControlPlaneException.invalidRequest("name is required"));
    }
    validateFields(
        failureThreshold,
        rollingWindowSeconds,
        openDurationSeconds,
        halfOpenMaxRequests,
        successThreshold,
        predicateCountHttp5xx,
        predicateCountConnectFailure,
        predicateCountConnectTimeout,
        predicateCountReadTimeout,
        predicateCountTlsFailure,
        predicateCountTransportException,
        predicateCountHttp429);
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    CircuitBreakerPolicyEntity entity =
        new CircuitBreakerPolicyEntity(
            UUID.randomUUID(),
            apiId,
            name.trim(),
            failureThreshold,
            rollingWindowSeconds,
            openDurationSeconds,
            halfOpenMaxRequests,
            successThreshold,
            predicateCountHttp5xx,
            predicateCountConnectFailure,
            predicateCountConnectTimeout,
            predicateCountReadTimeout,
            predicateCountTlsFailure,
            predicateCountTransportException,
            predicateCountHttp429,
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
                        "A circuit breaker policy with that name exists")));
  }

  public Flux<CircuitBreakerPolicyEntity> list(UUID apiId) {
    return apiDefinitionService.get(apiId).thenMany(policyRepository.findByApiId(apiId));
  }

  public Mono<CircuitBreakerPolicyEntity> get(UUID apiId, UUID policyId) {
    return apiDefinitionService
        .get(apiId)
        .then(policyRepository.findById(policyId))
        .switchIfEmpty(
            Mono.error(ControlPlaneException.notFound("Circuit breaker policy was not found")))
        .flatMap(
            policy ->
                policy.apiId().equals(apiId)
                    ? Mono.just(policy)
                    : Mono.error(
                        ControlPlaneException.notFound("Circuit breaker policy was not found")));
  }

  public Mono<CircuitBreakerPolicyEntity> patch(
      UUID apiId,
      UUID policyId,
      Integer failureThreshold,
      Integer rollingWindowSeconds,
      Integer openDurationSeconds,
      Integer halfOpenMaxRequests,
      Integer successThreshold,
      Boolean predicateCountHttp5xx,
      Boolean predicateCountConnectFailure,
      Boolean predicateCountConnectTimeout,
      Boolean predicateCountReadTimeout,
      Boolean predicateCountTlsFailure,
      Boolean predicateCountTransportException,
      Boolean predicateCountHttp429,
      Boolean enabled) {
    return get(apiId, policyId)
        .flatMap(
            existing -> {
              int nextFailureThreshold =
                  failureThreshold == null ? existing.failureThreshold() : failureThreshold;
              int nextRollingWindow =
                  rollingWindowSeconds == null
                      ? existing.rollingWindowSeconds()
                      : rollingWindowSeconds;
              int nextOpenDuration =
                  openDurationSeconds == null
                      ? existing.openDurationSeconds()
                      : openDurationSeconds;
              int nextHalfOpenMax =
                  halfOpenMaxRequests == null
                      ? existing.halfOpenMaxRequests()
                      : halfOpenMaxRequests;
              int nextSuccessThreshold =
                  successThreshold == null ? existing.successThreshold() : successThreshold;
              boolean nextHttp5xx =
                  predicateCountHttp5xx == null
                      ? existing.predicateCountHttp5xx()
                      : predicateCountHttp5xx;
              boolean nextConnectFailure =
                  predicateCountConnectFailure == null
                      ? existing.predicateCountConnectFailure()
                      : predicateCountConnectFailure;
              boolean nextConnectTimeout =
                  predicateCountConnectTimeout == null
                      ? existing.predicateCountConnectTimeout()
                      : predicateCountConnectTimeout;
              boolean nextReadTimeout =
                  predicateCountReadTimeout == null
                      ? existing.predicateCountReadTimeout()
                      : predicateCountReadTimeout;
              boolean nextTlsFailure =
                  predicateCountTlsFailure == null
                      ? existing.predicateCountTlsFailure()
                      : predicateCountTlsFailure;
              boolean nextTransportException =
                  predicateCountTransportException == null
                      ? existing.predicateCountTransportException()
                      : predicateCountTransportException;
              boolean nextHttp429 =
                  predicateCountHttp429 == null
                      ? existing.predicateCountHttp429()
                      : predicateCountHttp429;
              boolean nextEnabled = enabled == null ? existing.enabled() : enabled;
              validateFields(
                  nextFailureThreshold,
                  nextRollingWindow,
                  nextOpenDuration,
                  nextHalfOpenMax,
                  nextSuccessThreshold,
                  nextHttp5xx,
                  nextConnectFailure,
                  nextConnectTimeout,
                  nextReadTimeout,
                  nextTlsFailure,
                  nextTransportException,
                  nextHttp429);
              OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
              return policyRepositoryCustom.patch(
                  existing.id(),
                  apiId,
                  nextFailureThreshold,
                  nextRollingWindow,
                  nextOpenDuration,
                  nextHalfOpenMax,
                  nextSuccessThreshold,
                  nextHttp5xx,
                  nextConnectFailure,
                  nextConnectTimeout,
                  nextReadTimeout,
                  nextTlsFailure,
                  nextTransportException,
                  nextHttp429,
                  nextEnabled,
                  now);
            });
  }

  public Mono<Void> delete(UUID apiId, UUID policyId) {
    return get(apiId, policyId).flatMap(policy -> policyRepository.deleteById(policy.id()));
  }

  public static void validateFields(
      int failureThreshold,
      int rollingWindowSeconds,
      int openDurationSeconds,
      int halfOpenMaxRequests,
      int successThreshold,
      boolean predicateCountHttp5xx,
      boolean predicateCountConnectFailure,
      boolean predicateCountConnectTimeout,
      boolean predicateCountReadTimeout,
      boolean predicateCountTlsFailure,
      boolean predicateCountTransportException,
      boolean predicateCountHttp429) {
    if (failureThreshold < MIN_FAILURE_THRESHOLD || failureThreshold > MAX_FAILURE_THRESHOLD) {
      throw ControlPlaneException.invalidRequest(
          "failureThreshold must be between "
              + MIN_FAILURE_THRESHOLD
              + " and "
              + MAX_FAILURE_THRESHOLD);
    }
    if (rollingWindowSeconds < MIN_ROLLING_WINDOW_SECONDS
        || rollingWindowSeconds > MAX_ROLLING_WINDOW_SECONDS) {
      throw ControlPlaneException.invalidRequest(
          "rollingWindowSeconds must be between "
              + MIN_ROLLING_WINDOW_SECONDS
              + " and "
              + MAX_ROLLING_WINDOW_SECONDS);
    }
    if (openDurationSeconds < MIN_OPEN_DURATION_SECONDS
        || openDurationSeconds > MAX_OPEN_DURATION_SECONDS) {
      throw ControlPlaneException.invalidRequest(
          "openDurationSeconds must be between "
              + MIN_OPEN_DURATION_SECONDS
              + " and "
              + MAX_OPEN_DURATION_SECONDS);
    }
    if (halfOpenMaxRequests < MIN_HALF_OPEN_MAX_REQUESTS
        || halfOpenMaxRequests > MAX_HALF_OPEN_MAX_REQUESTS) {
      throw ControlPlaneException.invalidRequest(
          "halfOpenMaxRequests must be between "
              + MIN_HALF_OPEN_MAX_REQUESTS
              + " and "
              + MAX_HALF_OPEN_MAX_REQUESTS);
    }
    if (successThreshold < MIN_SUCCESS_THRESHOLD || successThreshold > MAX_SUCCESS_THRESHOLD) {
      throw ControlPlaneException.invalidRequest(
          "successThreshold must be between "
              + MIN_SUCCESS_THRESHOLD
              + " and "
              + MAX_SUCCESS_THRESHOLD);
    }
    if (successThreshold > halfOpenMaxRequests) {
      throw ControlPlaneException.invalidRequest(
          "successThreshold must not exceed halfOpenMaxRequests");
    }
    if (!predicateCountHttp5xx
        && !predicateCountConnectFailure
        && !predicateCountConnectTimeout
        && !predicateCountReadTimeout
        && !predicateCountTlsFailure
        && !predicateCountTransportException
        && !predicateCountHttp429) {
      throw ControlPlaneException.invalidRequest("At least one failure predicate must be enabled");
    }
  }
}
