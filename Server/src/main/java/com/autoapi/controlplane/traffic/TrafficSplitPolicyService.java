package com.autoapi.controlplane.traffic;

import com.autoapi.controlplane.api.ControlPlaneException;
import com.autoapi.controlplane.apidefinition.ApiDefinitionService;
import com.autoapi.controlplane.persistence.TrafficSplitDestinationEntity;
import com.autoapi.controlplane.persistence.TrafficSplitDestinationRepository;
import com.autoapi.controlplane.persistence.TrafficSplitPolicyEntity;
import com.autoapi.controlplane.persistence.TrafficSplitPolicyRepository;
import com.autoapi.controlplane.persistence.TrafficSplitPolicyRepositoryCustom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
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
public class TrafficSplitPolicyService {

  public static final Set<String> SUPPORTED_SELECTION_KEYS =
      Set.of("REQUEST_ID", "API_KEY_ID", "HEADER", "COOKIE");

  public static final Set<String> SUPPORTED_FALLBACK_MODES =
      Set.of("STRICT", "FALLBACK_TO_ANY_HEALTHY_SPLIT", "FALLBACK_TO_PRIMARY");

  private static final Set<String> FORBIDDEN_HEADER_NAMES =
      Set.of(
          "AUTHORIZATION", "PROXY-AUTHORIZATION", "COOKIE", "SET-COOKIE", "X-API-KEY", "API-KEY");

  private static final Pattern SAFE_TOKEN_NAME = Pattern.compile("^[A-Za-z0-9!#$&\\-^_`.]+$");

  private final TrafficSplitPolicyRepository policyRepository;
  private final TrafficSplitPolicyRepositoryCustom policyRepositoryCustom;
  private final TrafficSplitDestinationRepository destinationRepository;
  private final ApiDefinitionService apiDefinitionService;

  public TrafficSplitPolicyService(
      TrafficSplitPolicyRepository policyRepository,
      TrafficSplitPolicyRepositoryCustom policyRepositoryCustom,
      TrafficSplitDestinationRepository destinationRepository,
      ApiDefinitionService apiDefinitionService) {
    this.policyRepository = policyRepository;
    this.policyRepositoryCustom = policyRepositoryCustom;
    this.destinationRepository = destinationRepository;
    this.apiDefinitionService = apiDefinitionService;
  }

  public Mono<TrafficSplitPolicyEntity> create(
      UUID apiId,
      String name,
      String selectionKey,
      String selectionKeyName,
      String fallbackMode,
      boolean enabled) {
    if (name == null || name.isBlank()) {
      return Mono.error(ControlPlaneException.invalidRequest("name is required"));
    }
    validatePolicyFields(selectionKey, selectionKeyName, fallbackMode);
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    TrafficSplitPolicyEntity entity =
        new TrafficSplitPolicyEntity(
            UUID.randomUUID(),
            apiId,
            name.trim(),
            normalizeEnum(selectionKey),
            normalizeOptionalName(selectionKeyName),
            normalizeEnum(fallbackMode),
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
                        "A traffic split policy with that name exists")));
  }

  public Flux<TrafficSplitPolicyEntity> list(UUID apiId) {
    return apiDefinitionService.get(apiId).thenMany(policyRepository.findByApiId(apiId));
  }

  public Mono<TrafficSplitPolicyEntity> get(UUID apiId, UUID policyId) {
    return apiDefinitionService
        .get(apiId)
        .then(policyRepository.findById(policyId))
        .switchIfEmpty(
            Mono.error(ControlPlaneException.notFound("Traffic split policy was not found")))
        .flatMap(
            policy -> {
              if (!policy.apiId().equals(apiId)) {
                return Mono.error(
                    ControlPlaneException.notFound("Traffic split policy was not found"));
              }
              return Mono.just(policy);
            });
  }

  public Mono<TrafficSplitPolicyEntity> getByIdOnly(UUID policyId) {
    return policyRepository
        .findById(policyId)
        .switchIfEmpty(
            Mono.error(ControlPlaneException.notFound("Traffic split policy was not found")));
  }

  public Mono<TrafficSplitPolicyEntity> patch(
      UUID apiId,
      UUID policyId,
      String name,
      String selectionKey,
      String selectionKeyName,
      String fallbackMode,
      Boolean enabled) {
    return get(apiId, policyId)
        .flatMap(
            existing -> {
              String mergedName = name == null ? existing.name() : name.trim();
              if (mergedName.isBlank()) {
                return Mono.error(ControlPlaneException.invalidRequest("name must not be blank"));
              }
              String mergedSelectionKey =
                  selectionKey == null ? existing.selectionKey() : normalizeEnum(selectionKey);
              String mergedSelectionKeyName =
                  selectionKeyName == null
                      ? existing.selectionKeyName()
                      : normalizeOptionalName(selectionKeyName);
              String mergedFallbackMode =
                  fallbackMode == null ? existing.fallbackMode() : normalizeEnum(fallbackMode);
              boolean mergedEnabled = enabled == null ? existing.enabled() : enabled;
              validatePolicyFields(mergedSelectionKey, mergedSelectionKeyName, mergedFallbackMode);
              OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
              return policyRepositoryCustom
                  .patchPolicy(
                      policyId,
                      mergedName,
                      mergedSelectionKey,
                      mergedSelectionKeyName,
                      mergedFallbackMode,
                      mergedEnabled,
                      now)
                  .onErrorResume(
                      DataIntegrityViolationException.class,
                      ex ->
                          Mono.error(
                              ControlPlaneException.conflict(
                                  "A traffic split policy with that name exists")));
            });
  }

  public Flux<TrafficSplitDestinationEntity> listDestinations(UUID policyId) {
    return destinationRepository.findByTrafficSplitPolicyId(policyId);
  }

  public static void validatePolicyFields(
      String selectionKey, String selectionKeyName, String fallbackMode) {
    if (selectionKey == null || !SUPPORTED_SELECTION_KEYS.contains(normalizeEnum(selectionKey))) {
      throw ControlPlaneException.invalidRequest("Unsupported selectionKey");
    }
    String normalizedKey = normalizeEnum(selectionKey);
    if (("REQUEST_ID".equals(normalizedKey) || "API_KEY_ID".equals(normalizedKey))
        && selectionKeyName != null
        && !selectionKeyName.isBlank()) {
      throw ControlPlaneException.invalidRequest(
          "selectionKeyName must be omitted for REQUEST_ID and API_KEY_ID");
    }
    if (("HEADER".equals(normalizedKey) || "COOKIE".equals(normalizedKey))
        && (selectionKeyName == null || selectionKeyName.isBlank())) {
      throw ControlPlaneException.invalidRequest(
          "selectionKeyName is required for HEADER and COOKIE selection keys");
    }
    if ("HEADER".equals(normalizedKey)) {
      validateHeaderName(selectionKeyName);
    }
    if ("COOKIE".equals(normalizedKey)) {
      validateCookieName(selectionKeyName);
    }
    if (fallbackMode == null || !SUPPORTED_FALLBACK_MODES.contains(normalizeEnum(fallbackMode))) {
      throw ControlPlaneException.invalidRequest("Unsupported fallbackMode");
    }
  }

  public static void validateHeaderName(String headerName) {
    if (headerName == null || headerName.isBlank()) {
      throw ControlPlaneException.invalidRequest("Header name is required");
    }
    String trimmed = headerName.trim();
    if (!SAFE_TOKEN_NAME.matcher(trimmed).matches()) {
      throw ControlPlaneException.invalidRequest("Invalid header name");
    }
    if (FORBIDDEN_HEADER_NAMES.contains(trimmed.toUpperCase(Locale.ROOT))) {
      throw ControlPlaneException.invalidRequest(
          "Header name is not allowed for traffic splitting");
    }
  }

  public static void validateCookieName(String cookieName) {
    if (cookieName == null || cookieName.isBlank()) {
      throw ControlPlaneException.invalidRequest("Cookie name is required");
    }
    if (!SAFE_TOKEN_NAME.matcher(cookieName.trim()).matches()) {
      throw ControlPlaneException.invalidRequest("Invalid cookie name");
    }
  }

  public static void validateDestinationWeight(int weight) {
    if (weight < 0 || weight > 10000) {
      throw ControlPlaneException.invalidRequest("weight must be between 0 and 10000");
    }
  }

  public static void validateDestinationPriority(int priority) {
    if (priority < 0) {
      throw ControlPlaneException.invalidRequest("priority must be non-negative");
    }
  }

  private static String normalizeEnum(String value) {
    return value.trim().toUpperCase(Locale.ROOT);
  }

  private static String normalizeOptionalName(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
