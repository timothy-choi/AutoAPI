package com.autoapi.controlplane.validation;

import com.autoapi.config.HostNormalizer;
import com.autoapi.controlplane.DraftGraphService;
import com.autoapi.controlplane.backendhealth.BackendHealthPolicyService;
import com.autoapi.controlplane.configversion.CompiledGatewaySection;
import com.autoapi.controlplane.configversion.HashableRuntimePayload;
import com.autoapi.controlplane.configversion.RuntimeConfigCompiler;
import com.autoapi.controlplane.configversion.RuntimeContentHasher;
import com.autoapi.controlplane.persistence.ApiEntity;
import com.autoapi.controlplane.persistence.ApiKeyEntity;
import com.autoapi.controlplane.persistence.BackendHealthPolicyEntity;
import com.autoapi.controlplane.persistence.RateLimitPolicyEntity;
import com.autoapi.controlplane.persistence.RouteEntity;
import com.autoapi.controlplane.persistence.RoutePolicyBindingEntity;
import com.autoapi.controlplane.persistence.UpstreamPoolEntity;
import com.autoapi.controlplane.persistence.UpstreamTargetEntity;
import com.autoapi.security.ApiKeyDigestService;
import com.autoapi.validation.UpstreamUriValidator;
import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class DraftGraphValidator {

  private static final Set<String> SUPPORTED_METHODS =
      Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS");

  private DraftGraphValidator() {}

  public static ValidationResult validate(DraftGraphService.DraftGraph graph) {
    return validate(
        graph.api(),
        graph.routes(),
        graph.pools(),
        graph.targets(),
        graph.apiKeys(),
        graph.rateLimitPolicies(),
        graph.backendHealthPolicies(),
        graph.routePolicyBindings(),
        graph.gatewayDefaults());
  }

  public static ValidationResult validate(
      ApiEntity api,
      List<RouteEntity> routes,
      List<UpstreamPoolEntity> pools,
      List<UpstreamTargetEntity> targets,
      List<ApiKeyEntity> apiKeys,
      List<RateLimitPolicyEntity> rateLimitPolicies,
      List<BackendHealthPolicyEntity> backendHealthPolicies,
      List<RoutePolicyBindingEntity> routePolicyBindings,
      CompiledGatewaySection gatewayDefaults) {
    List<ValidationError> errors = new ArrayList<>();
    OffsetDateTime publishInstant = OffsetDateTime.now(ZoneOffset.UTC);

    if (api == null) {
      errors.add(new ValidationError("API_NOT_FOUND", null, "API was not found"));
      return ValidationResult.invalid(errors);
    }
    if (!api.enabled()) {
      errors.add(new ValidationError("API_DISABLED", api.id(), "API is disabled"));
    }

    Map<UUID, UpstreamPoolEntity> poolById =
        pools.stream().collect(Collectors.toMap(UpstreamPoolEntity::id, p -> p));
    Map<UUID, List<UpstreamTargetEntity>> targetsByPool =
        targets.stream().collect(Collectors.groupingBy(UpstreamTargetEntity::upstreamPoolId));
    Map<UUID, RateLimitPolicyEntity> policyById =
        rateLimitPolicies.stream().collect(Collectors.toMap(RateLimitPolicyEntity::id, p -> p));
    Map<UUID, BackendHealthPolicyEntity> healthPolicyById =
        backendHealthPolicies.stream()
            .collect(Collectors.toMap(BackendHealthPolicyEntity::id, p -> p));
    Map<UUID, RoutePolicyBindingEntity> bindingByRouteId =
        routePolicyBindings.stream()
            .collect(Collectors.toMap(RoutePolicyBindingEntity::routeId, b -> b));

    for (UpstreamPoolEntity pool : pools) {
      validatePool(
          pool, targetsByPool.getOrDefault(pool.id(), List.of()), healthPolicyById, errors);
    }

    List<RouteEntity> enabledRoutes =
        routes.stream().filter(RouteEntity::enabled).sorted(routeDraftComparator()).toList();
    if (enabledRoutes.isEmpty()) {
      errors.add(
          new ValidationError(
              "NO_ENABLED_ROUTES", api.id(), "At least one enabled route is required"));
    }

    for (RouteEntity route : enabledRoutes) {
      validateRoute(route, api.id(), poolById, errors);
      validateRoutePolicyBinding(route, api.id(), bindingByRouteId, policyById, errors);
    }

    validateRouteAmbiguity(enabledRoutes, errors);
    validateApiKeys(apiKeys, publishInstant, errors);

    long publishableKeyCount =
        apiKeys.stream()
            .filter(key -> RuntimeConfigCompiler.isPublishableAt(key, publishInstant))
            .count();
    for (RouteEntity route : enabledRoutes) {
      RoutePolicyBindingEntity binding = bindingByRouteId.get(route.id());
      if (binding != null && binding.authenticationRequired() && publishableKeyCount == 0) {
        errors.add(
            new ValidationError(
                "AUTH_ROUTE_NO_API_KEYS",
                route.id(),
                "Authentication-required route has no enabled, non-revoked, unexpired API keys"));
      }
    }

    if (!errors.isEmpty()) {
      return ValidationResult.invalid(errors);
    }

    HashableRuntimePayload payload =
        RuntimeConfigCompiler.compile(
            api.id(),
            gatewayDefaults,
            enabledRoutes,
            poolById,
            targetsByPool,
            bindingByRouteId,
            policyById,
            healthPolicyById,
            apiKeys,
            publishInstant);
    validateCompiledPayload(payload, errors);
    if (!errors.isEmpty()) {
      return ValidationResult.invalid(errors);
    }

    String contentHash =
        RuntimeContentHasher.sha256Hex(RuntimeContentHasher.canonicalJson(payload));
    ValidationSummary summary =
        new ValidationSummary(
            enabledRoutes.size(),
            pools.size(),
            (int) targets.stream().filter(UpstreamTargetEntity::enabled).count());
    return ValidationResult.valid(contentHash, summary);
  }

  /** Backward-compatible entry for tests that omit security draft resources. */
  public static ValidationResult validate(
      ApiEntity api,
      List<RouteEntity> routes,
      List<UpstreamPoolEntity> pools,
      List<UpstreamTargetEntity> targets,
      CompiledGatewaySection gatewayDefaults) {
    return validate(
        api, routes, pools, targets, List.of(), List.of(), List.of(), List.of(), gatewayDefaults);
  }

  private static void validateCompiledPayload(
      HashableRuntimePayload payload, List<ValidationError> errors) {
    for (var key : payload.apiKeys()) {
      if (key.secretDigest() == null
          || key.secretDigest().length() != ApiKeyDigestService.DIGEST_LENGTH_BYTES * 2) {
        errors.add(
            new ValidationError(
                "API_KEY_DIGEST_INVALID",
                null,
                "Compiled API key digest must be a 64-character hex SHA-256 HMAC value"));
      }
    }
  }

  private static void validateApiKeys(
      List<ApiKeyEntity> apiKeys, OffsetDateTime publishInstant, List<ValidationError> errors) {
    for (ApiKeyEntity key : apiKeys) {
      if (key.secretDigest() == null
          || key.secretDigest().length != ApiKeyDigestService.DIGEST_LENGTH_BYTES) {
        errors.add(
            new ValidationError(
                "API_KEY_DIGEST_INVALID", key.id(), "API key secret digest has invalid length"));
      }
      if (key.revokedAt() != null && RuntimeConfigCompiler.isPublishableAt(key, publishInstant)) {
        errors.add(
            new ValidationError(
                "API_KEY_REVOKED_INCLUDED", key.id(), "Revoked API key cannot be published"));
      }
    }
  }

  private static void validateRoutePolicyBinding(
      RouteEntity route,
      UUID apiId,
      Map<UUID, RoutePolicyBindingEntity> bindingByRouteId,
      Map<UUID, RateLimitPolicyEntity> policyById,
      List<ValidationError> errors) {
    RoutePolicyBindingEntity binding = bindingByRouteId.get(route.id());
    if (binding == null) {
      return;
    }
    if (binding.rateLimitPolicyId() != null && !binding.authenticationRequired()) {
      errors.add(
          new ValidationError(
              "RATE_LIMIT_REQUIRES_AUTH",
              route.id(),
              "Rate limit policy requires authenticationRequired=true"));
    }
    if (binding.rateLimitPolicyId() == null) {
      return;
    }
    RateLimitPolicyEntity policy = policyById.get(binding.rateLimitPolicyId());
    if (policy == null) {
      errors.add(
          new ValidationError(
              "RATE_LIMIT_POLICY_NOT_FOUND",
              route.id(),
              "Route references missing rate limit policy"));
      return;
    }
    if (!policy.apiId().equals(apiId)) {
      errors.add(
          new ValidationError(
              "RATE_LIMIT_POLICY_WRONG_API",
              route.id(),
              "Rate limit policy belongs to another API"));
    }
    if (!policy.enabled()) {
      errors.add(
          new ValidationError(
              "RATE_LIMIT_POLICY_DISABLED",
              policy.id(),
              "Disabled rate limit policy cannot be bound"));
    }
    validateRateLimitPolicyFields(policy, errors);
  }

  private static void validateRateLimitPolicyFields(
      RateLimitPolicyEntity policy, List<ValidationError> errors) {
    if (!"API_KEY".equals(policy.identitySource())) {
      errors.add(
          new ValidationError(
              "RATE_LIMIT_IDENTITY_UNSUPPORTED",
              policy.id(),
              "Unsupported identity source: " + policy.identitySource()));
    }
    if (!"FAIL_OPEN".equals(policy.redisFailureMode())
        && !"FAIL_CLOSED".equals(policy.redisFailureMode())) {
      errors.add(
          new ValidationError(
              "RATE_LIMIT_FAILURE_MODE_UNSUPPORTED",
              policy.id(),
              "Unsupported redis failure mode: " + policy.redisFailureMode()));
    }
    if (policy.limitCount() <= 0 || policy.limitCount() > 10_000_000) {
      errors.add(
          new ValidationError(
              "RATE_LIMIT_COUNT_INVALID",
              policy.id(),
              "limitCount must be between 1 and 10000000"));
    }
    if (policy.windowSeconds() <= 0 || policy.windowSeconds() > 86_400) {
      errors.add(
          new ValidationError(
              "RATE_LIMIT_WINDOW_INVALID",
              policy.id(),
              "windowSeconds must be between 1 and 86400"));
    }
  }

  private static void validatePool(
      UpstreamPoolEntity pool,
      List<UpstreamTargetEntity> poolTargets,
      Map<UUID, BackendHealthPolicyEntity> healthPolicyById,
      List<ValidationError> errors) {
    if (!"ROUND_ROBIN".equals(pool.loadBalancing())) {
      errors.add(
          new ValidationError(
              "POOL_UNSUPPORTED_LOAD_BALANCING",
              pool.id(),
              "Unsupported load balancing algorithm: " + pool.loadBalancing()));
    }
    if (pool.backendHealthPolicyId() != null) {
      BackendHealthPolicyEntity policy = healthPolicyById.get(pool.backendHealthPolicyId());
      if (policy == null) {
        errors.add(
            new ValidationError(
                "BACKEND_HEALTH_POLICY_NOT_FOUND",
                pool.id(),
                "Upstream pool references missing backend health policy"));
      } else {
        if (!policy.apiId().equals(pool.apiId())) {
          errors.add(
              new ValidationError(
                  "BACKEND_HEALTH_POLICY_WRONG_API",
                  pool.id(),
                  "Backend health policy belongs to another API"));
        }
        if (!policy.enabled()) {
          errors.add(
              new ValidationError(
                  "BACKEND_HEALTH_POLICY_DISABLED",
                  policy.id(),
                  "Disabled backend health policy cannot be bound"));
        }
        try {
          BackendHealthPolicyService.validateFields(
              policy.consecutiveFailureThreshold(),
              policy.ejectionDurationSeconds(),
              policy.maxEjectionPercent());
        } catch (RuntimeException ex) {
          errors.add(
              new ValidationError(
                  "BACKEND_HEALTH_POLICY_INVALID", policy.id(), sanitizeMessage(ex.getMessage())));
        }
      }
    }
    long enabledCount = poolTargets.stream().filter(UpstreamTargetEntity::enabled).count();
    if (enabledCount == 0) {
      errors.add(
          new ValidationError(
              "POOL_NO_ENABLED_TARGETS",
              pool.id(),
              "Upstream pool " + pool.name() + " has no enabled targets"));
    }
    for (UpstreamTargetEntity target : poolTargets) {
      if (!target.enabled()) {
        continue;
      }
      validateTargetUrl(target, errors);
    }
  }

  private static void validateTargetUrl(UpstreamTargetEntity target, List<ValidationError> errors) {
    try {
      UpstreamUriValidator.validate(URI.create(target.url()), "target: " + target.id());
    } catch (RuntimeException ex) {
      errors.add(
          new ValidationError(
              "UPSTREAM_URL_INVALID", target.id(), sanitizeMessage(ex.getMessage())));
    }
  }

  private static void validateRoute(
      RouteEntity route,
      UUID apiId,
      Map<UUID, UpstreamPoolEntity> poolById,
      List<ValidationError> errors) {
    if (route.host() == null || route.host().isBlank()) {
      errors.add(
          new ValidationError("ROUTE_HOST_BLANK", route.id(), "Route host must not be blank"));
    } else {
      try {
        HostNormalizer.normalize(route.host());
      } catch (RuntimeException ex) {
        errors.add(
            new ValidationError(
                "ROUTE_HOST_INVALID", route.id(), sanitizeMessage(ex.getMessage())));
      }
    }

    String prefix = route.pathPrefix();
    if (prefix == null || prefix.isBlank() || !prefix.startsWith("/")) {
      errors.add(
          new ValidationError(
              "ROUTE_PATH_PREFIX_INVALID", route.id(), "Route pathPrefix must begin with '/'"));
    }

    if (route.methods() == null || route.methods().length == 0) {
      errors.add(
          new ValidationError(
              "ROUTE_METHODS_EMPTY", route.id(), "Route methods must not be empty"));
    } else {
      for (String method : route.methods()) {
        if (method == null || !SUPPORTED_METHODS.contains(method.toUpperCase(Locale.ROOT))) {
          errors.add(
              new ValidationError(
                  "ROUTE_METHOD_UNSUPPORTED", route.id(), "Unsupported HTTP method: " + method));
        }
      }
    }

    UpstreamPoolEntity pool = poolById.get(route.upstreamPoolId());
    if (pool == null) {
      errors.add(
          new ValidationError(
              "ROUTE_POOL_NOT_FOUND", route.id(), "Route references missing upstream pool"));
    } else if (!pool.apiId().equals(apiId)) {
      errors.add(
          new ValidationError(
              "ROUTE_POOL_WRONG_API",
              route.id(),
              "Route references upstream pool from another API"));
    }
  }

  private static void validateRouteAmbiguity(
      List<RouteEntity> routes, List<ValidationError> errors) {
    for (int i = 0; i < routes.size(); i++) {
      RouteEntity a = routes.get(i);
      String hostA = HostNormalizer.normalize(a.host());
      String prefixA = normalizePrefix(a.pathPrefix());
      Set<String> methodsA = methodSet(a.methods());
      for (int j = i + 1; j < routes.size(); j++) {
        RouteEntity b = routes.get(j);
        String hostB = HostNormalizer.normalize(b.host());
        String prefixB = normalizePrefix(b.pathPrefix());
        if (!hostA.equals(hostB) || !prefixA.equals(prefixB)) {
          continue;
        }
        for (String method : methodsA) {
          if (methodSet(b.methods()).contains(method)) {
            errors.add(
                new ValidationError(
                    "ROUTE_AMBIGUOUS",
                    a.id(),
                    "Ambiguous routes '"
                        + a.name()
                        + "' and '"
                        + b.name()
                        + "' for host "
                        + hostA
                        + ", path prefix "
                        + prefixA
                        + ", method "
                        + method));
          }
        }
      }
    }
  }

  private static Set<String> methodSet(String[] methods) {
    Set<String> set = new HashSet<>();
    if (methods != null) {
      for (String method : methods) {
        if (method != null) {
          set.add(method.toUpperCase(Locale.ROOT));
        }
      }
    }
    return set;
  }

  private static String normalizePrefix(String prefix) {
    if ("/".equals(prefix)) {
      return "/";
    }
    return prefix.endsWith("/") ? prefix.substring(0, prefix.length() - 1) : prefix;
  }

  private static Comparator<RouteEntity> routeDraftComparator() {
    return Comparator.comparing((RouteEntity r) -> HostNormalizer.normalize(r.host()))
        .thenComparing((RouteEntity r) -> -normalizePrefix(r.pathPrefix()).length())
        .thenComparing(r -> normalizePrefix(r.pathPrefix()))
        .thenComparing(RouteEntity::id);
  }

  private static String sanitizeMessage(String message) {
    return message == null ? "Validation failed" : message;
  }
}
