package com.autoapi.controlplane.validation;

import com.autoapi.config.HostNormalizer;
import com.autoapi.controlplane.configversion.CompiledGatewaySection;
import com.autoapi.controlplane.configversion.HashableRuntimePayload;
import com.autoapi.controlplane.configversion.RuntimeConfigCompiler;
import com.autoapi.controlplane.configversion.RuntimeContentHasher;
import com.autoapi.controlplane.persistence.ApiEntity;
import com.autoapi.controlplane.persistence.RouteEntity;
import com.autoapi.controlplane.persistence.UpstreamPoolEntity;
import com.autoapi.controlplane.persistence.UpstreamTargetEntity;
import com.autoapi.validation.UpstreamUriValidator;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class DraftGraphValidator {

  private static final Set<String> SUPPORTED_METHODS =
      Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS");

  private DraftGraphValidator() {}

  public static ValidationResult validate(
      ApiEntity api,
      List<RouteEntity> routes,
      List<UpstreamPoolEntity> pools,
      List<UpstreamTargetEntity> targets,
      CompiledGatewaySection gatewayDefaults) {
    List<ValidationError> errors = new ArrayList<>();

    if (api == null) {
      errors.add(new ValidationError("API_NOT_FOUND", null, "API was not found"));
      return ValidationResult.invalid(errors);
    }
    if (!api.enabled()) {
      errors.add(new ValidationError("API_DISABLED", api.id(), "API is disabled"));
    }

    Map<UUID, UpstreamPoolEntity> poolById =
        pools.stream().collect(java.util.stream.Collectors.toMap(UpstreamPoolEntity::id, p -> p));
    Map<UUID, List<UpstreamTargetEntity>> targetsByPool =
        targets.stream()
            .collect(java.util.stream.Collectors.groupingBy(UpstreamTargetEntity::upstreamPoolId));

    for (UpstreamPoolEntity pool : pools) {
      validatePool(pool, targetsByPool.getOrDefault(pool.id(), List.of()), errors);
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
    }

    validateRouteAmbiguity(enabledRoutes, errors);

    if (!errors.isEmpty()) {
      return ValidationResult.invalid(errors);
    }

    HashableRuntimePayload payload =
        RuntimeConfigCompiler.compile(
            api.id(), gatewayDefaults, enabledRoutes, poolById, targetsByPool);
    String contentHash =
        RuntimeContentHasher.sha256Hex(RuntimeContentHasher.canonicalJson(payload));
    ValidationSummary summary =
        new ValidationSummary(
            enabledRoutes.size(),
            pools.size(),
            (int) targets.stream().filter(UpstreamTargetEntity::enabled).count());
    return ValidationResult.valid(contentHash, summary);
  }

  private static void validatePool(
      UpstreamPoolEntity pool,
      List<UpstreamTargetEntity> poolTargets,
      List<ValidationError> errors) {
    if (!"ROUND_ROBIN".equals(pool.loadBalancing())) {
      errors.add(
          new ValidationError(
              "POOL_UNSUPPORTED_LOAD_BALANCING",
              pool.id(),
              "Unsupported load balancing algorithm: " + pool.loadBalancing()));
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
