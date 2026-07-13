package com.autoapi.controlplane.retry;

import com.autoapi.controlplane.api.ControlPlaneException;
import com.autoapi.controlplane.apidefinition.ApiDefinitionService;
import com.autoapi.controlplane.persistence.RetryPolicyEntity;
import com.autoapi.controlplane.persistence.RetryPolicyRepository;
import com.autoapi.controlplane.persistence.RetryPolicyRepositoryCustom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
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
public class RetryPolicyService {

  public static final int MIN_MAX_ATTEMPTS = 1;
  public static final int MAX_MAX_ATTEMPTS = 5;
  public static final int MIN_TIMEOUT_MS = 50;
  public static final int MAX_TIMEOUT_MS = 30000;
  public static final int MAX_BUDGET_WINDOW_SECONDS = 300;

  private static final Set<String> SUPPORTED_METHODS =
      Set.of("GET", "HEAD", "OPTIONS", "PUT", "DELETE", "POST", "PATCH");

  private static final Set<String> UNSAFE_METHODS = Set.of("POST", "PATCH");

  private final RetryPolicyRepository policyRepository;
  private final RetryPolicyRepositoryCustom policyRepositoryCustom;
  private final ApiDefinitionService apiDefinitionService;

  public RetryPolicyService(
      RetryPolicyRepository policyRepository,
      RetryPolicyRepositoryCustom policyRepositoryCustom,
      ApiDefinitionService apiDefinitionService) {
    this.policyRepository = policyRepository;
    this.policyRepositoryCustom = policyRepositoryCustom;
    this.apiDefinitionService = apiDefinitionService;
  }

  public Mono<RetryPolicyEntity> create(
      UUID apiId,
      String name,
      int maxAttempts,
      int perAttemptTimeoutMs,
      boolean retryOnConnectFailure,
      boolean retryOnConnectionReset,
      boolean retryOnDnsFailure,
      boolean retryOnResponseTimeout,
      String[] retryableMethods,
      boolean requireIdempotencyKeyForUnsafeMethods,
      int budgetPercent,
      int budgetMinRetriesPerSecond,
      int budgetWindowSeconds,
      boolean enabled) {
    if (name == null || name.isBlank()) {
      return Mono.error(ControlPlaneException.invalidRequest("name is required"));
    }
    String[] normalizedMethods = normalizeMethods(retryableMethods);
    validateFields(
        maxAttempts,
        perAttemptTimeoutMs,
        retryOnConnectFailure,
        retryOnConnectionReset,
        retryOnDnsFailure,
        retryOnResponseTimeout,
        normalizedMethods,
        requireIdempotencyKeyForUnsafeMethods,
        budgetPercent,
        budgetMinRetriesPerSecond,
        budgetWindowSeconds);
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    RetryPolicyEntity entity =
        new RetryPolicyEntity(
            UUID.randomUUID(),
            apiId,
            name.trim(),
            maxAttempts,
            perAttemptTimeoutMs,
            retryOnConnectFailure,
            retryOnConnectionReset,
            retryOnDnsFailure,
            retryOnResponseTimeout,
            normalizedMethods,
            requireIdempotencyKeyForUnsafeMethods,
            budgetPercent,
            budgetMinRetriesPerSecond,
            budgetWindowSeconds,
            enabled,
            now,
            now);
    return apiDefinitionService
        .get(apiId)
        .then(policyRepository.save(entity))
        .onErrorResume(
            DataIntegrityViolationException.class,
            ex ->
                Mono.error(ControlPlaneException.conflict("A retry policy with that name exists")));
  }

  public Flux<RetryPolicyEntity> list(UUID apiId) {
    return apiDefinitionService.get(apiId).thenMany(policyRepository.findByApiId(apiId));
  }

  public Mono<RetryPolicyEntity> get(UUID apiId, UUID policyId) {
    return apiDefinitionService
        .get(apiId)
        .then(policyRepository.findById(policyId))
        .switchIfEmpty(Mono.error(ControlPlaneException.notFound("Retry policy was not found")))
        .flatMap(
            policy ->
                policy.apiId().equals(apiId)
                    ? Mono.just(policy)
                    : Mono.error(ControlPlaneException.notFound("Retry policy was not found")));
  }

  public Mono<RetryPolicyEntity> patch(
      UUID apiId,
      UUID policyId,
      Integer maxAttempts,
      Integer perAttemptTimeoutMs,
      Boolean retryOnConnectFailure,
      Boolean retryOnConnectionReset,
      Boolean retryOnDnsFailure,
      Boolean retryOnResponseTimeout,
      String[] retryableMethods,
      Boolean requireIdempotencyKeyForUnsafeMethods,
      Integer budgetPercent,
      Integer budgetMinRetriesPerSecond,
      Integer budgetWindowSeconds,
      Boolean enabled) {
    return get(apiId, policyId)
        .flatMap(
            existing -> {
              int nextMaxAttempts = maxAttempts == null ? existing.maxAttempts() : maxAttempts;
              int nextTimeout =
                  perAttemptTimeoutMs == null
                      ? existing.perAttemptTimeoutMs()
                      : perAttemptTimeoutMs;
              boolean nextConnect =
                  retryOnConnectFailure == null
                      ? existing.retryOnConnectFailure()
                      : retryOnConnectFailure;
              boolean nextReset =
                  retryOnConnectionReset == null
                      ? existing.retryOnConnectionReset()
                      : retryOnConnectionReset;
              boolean nextDns =
                  retryOnDnsFailure == null ? existing.retryOnDnsFailure() : retryOnDnsFailure;
              boolean nextResponseTimeout =
                  retryOnResponseTimeout == null
                      ? existing.retryOnResponseTimeout()
                      : retryOnResponseTimeout;
              String[] nextMethods =
                  retryableMethods == null ? existing.retryableMethods() : retryableMethods;
              nextMethods = normalizeMethods(nextMethods);
              boolean nextRequireKey =
                  requireIdempotencyKeyForUnsafeMethods == null
                      ? existing.requireIdempotencyKeyForUnsafeMethods()
                      : requireIdempotencyKeyForUnsafeMethods;
              int nextBudgetPercent =
                  budgetPercent == null ? existing.budgetPercent() : budgetPercent;
              int nextBudgetMin =
                  budgetMinRetriesPerSecond == null
                      ? existing.budgetMinRetriesPerSecond()
                      : budgetMinRetriesPerSecond;
              int nextBudgetWindow =
                  budgetWindowSeconds == null
                      ? existing.budgetWindowSeconds()
                      : budgetWindowSeconds;
              boolean nextEnabled = enabled == null ? existing.enabled() : enabled;
              validateFields(
                  nextMaxAttempts,
                  nextTimeout,
                  nextConnect,
                  nextReset,
                  nextDns,
                  nextResponseTimeout,
                  nextMethods,
                  nextRequireKey,
                  nextBudgetPercent,
                  nextBudgetMin,
                  nextBudgetWindow);
              OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
              return policyRepositoryCustom.update(
                  existing.id(),
                  nextMaxAttempts,
                  nextTimeout,
                  nextConnect,
                  nextReset,
                  nextDns,
                  nextResponseTimeout,
                  nextMethods,
                  nextRequireKey,
                  nextBudgetPercent,
                  nextBudgetMin,
                  nextBudgetWindow,
                  nextEnabled,
                  now);
            });
  }

  public static String[] normalizeMethods(String[] methods) {
    if (methods == null || methods.length == 0) {
      throw ControlPlaneException.invalidRequest("retryableMethods must not be empty");
    }
    TreeSet<String> normalized = new TreeSet<>();
    for (String method : methods) {
      if (method == null || method.isBlank()) {
        throw ControlPlaneException.invalidRequest("retryableMethods must not contain blanks");
      }
      String upper = method.trim().toUpperCase(Locale.ROOT);
      if (!SUPPORTED_METHODS.contains(upper)) {
        throw ControlPlaneException.invalidRequest("Unsupported retryable method: " + upper);
      }
      normalized.add(upper);
    }
    return normalized.toArray(String[]::new);
  }

  public static void validateFields(
      int maxAttempts,
      int perAttemptTimeoutMs,
      boolean retryOnConnectFailure,
      boolean retryOnConnectionReset,
      boolean retryOnDnsFailure,
      boolean retryOnResponseTimeout,
      String[] retryableMethods,
      boolean requireIdempotencyKeyForUnsafeMethods,
      int budgetPercent,
      int budgetMinRetriesPerSecond,
      int budgetWindowSeconds) {
    if (maxAttempts < MIN_MAX_ATTEMPTS || maxAttempts > MAX_MAX_ATTEMPTS) {
      throw ControlPlaneException.invalidRequest(
          "maxAttempts must be between " + MIN_MAX_ATTEMPTS + " and " + MAX_MAX_ATTEMPTS);
    }
    if (perAttemptTimeoutMs < MIN_TIMEOUT_MS || perAttemptTimeoutMs > MAX_TIMEOUT_MS) {
      throw ControlPlaneException.invalidRequest(
          "perAttemptTimeoutMs must be between " + MIN_TIMEOUT_MS + " and " + MAX_TIMEOUT_MS);
    }
    if (budgetPercent < 0 || budgetPercent > 100) {
      throw ControlPlaneException.invalidRequest("budgetPercent must be between 0 and 100");
    }
    if (budgetMinRetriesPerSecond < 0 || budgetMinRetriesPerSecond > 10000) {
      throw ControlPlaneException.invalidRequest(
          "budgetMinRetriesPerSecond must be between 0 and 10000");
    }
    if (budgetWindowSeconds < 1 || budgetWindowSeconds > MAX_BUDGET_WINDOW_SECONDS) {
      throw ControlPlaneException.invalidRequest(
          "budgetWindowSeconds must be between 1 and " + MAX_BUDGET_WINDOW_SECONDS);
    }
    String[] normalized = normalizeMethods(retryableMethods);
    if (maxAttempts > 1
        && !retryOnConnectFailure
        && !retryOnConnectionReset
        && !retryOnDnsFailure
        && !retryOnResponseTimeout) {
      throw ControlPlaneException.invalidRequest(
          "At least one retryable failure type must be enabled when maxAttempts > 1");
    }
    for (String method : normalized) {
      if (UNSAFE_METHODS.contains(method) && !requireIdempotencyKeyForUnsafeMethods) {
        throw ControlPlaneException.invalidRequest(
            "POST and PATCH require requireIdempotencyKeyForUnsafeMethods=true");
      }
    }
  }

  public static boolean methodsEqual(String[] left, String[] right) {
    return Arrays.equals(normalizeMethods(left), normalizeMethods(right));
  }
}
