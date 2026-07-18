package com.autoapi.controlplane.configversion;

import com.autoapi.controlplane.persistence.ApiKeyEntity;
import com.autoapi.controlplane.persistence.BackendHealthPolicyEntity;
import com.autoapi.controlplane.persistence.CircuitBreakerPolicyEntity;
import com.autoapi.controlplane.persistence.RateLimitPolicyEntity;
import com.autoapi.controlplane.persistence.RetryPolicyEntity;
import com.autoapi.controlplane.persistence.RouteEntity;
import com.autoapi.controlplane.persistence.RoutePolicyBindingEntity;
import com.autoapi.controlplane.persistence.TrafficSplitDestinationEntity;
import com.autoapi.controlplane.persistence.TrafficSplitPolicyEntity;
import com.autoapi.controlplane.persistence.UpstreamPoolEntity;
import com.autoapi.controlplane.persistence.UpstreamTargetEntity;
import com.autoapi.security.ApiKeyDigestService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class RuntimeConfigCompiler {

  private RuntimeConfigCompiler() {}

  public static HashableRuntimePayload compile(
      UUID apiId,
      CompiledGatewaySection gateway,
      List<RouteEntity> enabledRoutes,
      Map<UUID, UpstreamPoolEntity> poolById,
      Map<UUID, List<UpstreamTargetEntity>> targetsByPool,
      Map<UUID, RoutePolicyBindingEntity> bindingByRouteId,
      Map<UUID, RateLimitPolicyEntity> policyById,
      Map<UUID, BackendHealthPolicyEntity> healthPolicyById,
      Map<UUID, RetryPolicyEntity> retryPolicyById,
      Map<UUID, CircuitBreakerPolicyEntity> circuitBreakerPolicyById,
      Map<UUID, TrafficSplitPolicyEntity> trafficSplitPolicyById,
      Map<UUID, List<TrafficSplitDestinationEntity>> destinationsByPolicyId,
      List<ApiKeyEntity> apiKeys,
      OffsetDateTime publishInstant) {
    List<CompiledRouteSection> compiledRoutes = new ArrayList<>();
    List<RouteEntity> sortedRoutes = enabledRoutes.stream().sorted(routeComparator()).toList();
    for (RouteEntity route : sortedRoutes) {
      RoutePolicyBindingEntity binding = bindingByRouteId.get(route.id());
      CompiledAuthenticationSection authentication = null;
      CompiledRateLimitSection rateLimit = null;
      CompiledRetrySection retry = null;
      CompiledCircuitBreakerSection circuitBreaker = null;
      if (binding != null && binding.authenticationRequired()) {
        authentication = new CompiledAuthenticationSection(true);
        if (binding.rateLimitPolicyId() != null) {
          RateLimitPolicyEntity policy = policyById.get(binding.rateLimitPolicyId());
          if (policy != null) {
            rateLimit =
                new CompiledRateLimitSection(
                    policy.id(),
                    policy.limitCount(),
                    policy.windowSeconds(),
                    policy.identitySource(),
                    policy.redisFailureMode());
          }
        }
      }
      if (binding != null && binding.retryPolicyId() != null) {
        RetryPolicyEntity retryPolicy = retryPolicyById.get(binding.retryPolicyId());
        if (retryPolicy != null && retryPolicy.enabled()) {
          retry =
              new CompiledRetrySection(
                  retryPolicy.id(),
                  retryPolicy.maxAttempts(),
                  retryPolicy.perAttemptTimeoutMs(),
                  retryPolicy.retryOnConnectFailure(),
                  retryPolicy.retryOnConnectionReset(),
                  retryPolicy.retryOnDnsFailure(),
                  retryPolicy.retryOnResponseTimeout(),
                  java.util.Arrays.stream(retryPolicy.retryableMethods()).sorted().toList(),
                  retryPolicy.requireIdempotencyKeyForUnsafeMethods(),
                  retryPolicy.budgetPercent(),
                  retryPolicy.budgetMinRetriesPerSecond(),
                  retryPolicy.budgetWindowSeconds());
        }
      }
      if (binding != null && binding.circuitBreakerPolicyId() != null) {
        CircuitBreakerPolicyEntity cbPolicy =
            circuitBreakerPolicyById.get(binding.circuitBreakerPolicyId());
        if (cbPolicy != null && cbPolicy.enabled()) {
          circuitBreaker =
              new CompiledCircuitBreakerSection(
                  cbPolicy.id(),
                  cbPolicy.failureThreshold(),
                  cbPolicy.rollingWindowSeconds(),
                  cbPolicy.openDurationSeconds(),
                  cbPolicy.halfOpenMaxRequests(),
                  cbPolicy.successThreshold(),
                  new CompiledCircuitBreakerFailurePredicateSection(
                      cbPolicy.predicateCountHttp5xx(),
                      cbPolicy.predicateCountConnectFailure(),
                      cbPolicy.predicateCountConnectTimeout(),
                      cbPolicy.predicateCountReadTimeout(),
                      cbPolicy.predicateCountTlsFailure(),
                      cbPolicy.predicateCountTransportException(),
                      cbPolicy.predicateCountHttp429()));
        }
      }
      CompiledTrafficSplitSection trafficSplit = null;
      CompiledUpstreamPoolSection upstreamPool = null;
      if (binding != null && binding.trafficSplitPolicyId() != null) {
        TrafficSplitPolicyEntity splitPolicy =
            trafficSplitPolicyById.get(binding.trafficSplitPolicyId());
        List<TrafficSplitDestinationEntity> destinations =
            destinationsByPolicyId.getOrDefault(binding.trafficSplitPolicyId(), List.of());
        if (splitPolicy != null && splitPolicy.enabled()) {
          trafficSplit =
              compileTrafficSplit(
                  splitPolicy, destinations, poolById, targetsByPool, healthPolicyById);
        }
      } else if (route.upstreamPoolId() != null) {
        upstreamPool =
            compilePool(route.upstreamPoolId(), poolById, targetsByPool, healthPolicyById);
      }
      List<String> methods =
          java.util.Arrays.stream(route.methods())
              .map(m -> m.toUpperCase(Locale.ROOT))
              .sorted()
              .toList();
      compiledRoutes.add(
          new CompiledRouteSection(
              route.id(),
              route.host(),
              route.pathPrefix(),
              methods,
              authentication,
              rateLimit,
              retry,
              circuitBreaker,
              trafficSplit,
              upstreamPool));
    }
    List<CompiledApiKeySection> compiledKeys = compileApiKeys(apiKeys, publishInstant);
    return new HashableRuntimePayload(apiId, gateway, compiledRoutes, compiledKeys);
  }

  private static CompiledTrafficSplitSection compileTrafficSplit(
      TrafficSplitPolicyEntity policy,
      List<TrafficSplitDestinationEntity> destinations,
      Map<UUID, UpstreamPoolEntity> poolById,
      Map<UUID, List<UpstreamTargetEntity>> targetsByPool,
      Map<UUID, BackendHealthPolicyEntity> healthPolicyById) {
    List<TrafficSplitDestinationEntity> sortedDestinations =
        destinations.stream()
            .sorted(
                Comparator.comparing(TrafficSplitDestinationEntity::priority)
                    .thenComparing(TrafficSplitDestinationEntity::id))
            .toList();
    List<CompiledTrafficSplitDestinationSection> compiledDestinations = new ArrayList<>();
    for (TrafficSplitDestinationEntity destination : sortedDestinations) {
      compiledDestinations.add(
          new CompiledTrafficSplitDestinationSection(
              destination.id(),
              destination.name(),
              destination.weight(),
              destination.priority(),
              destination.primary(),
              compilePool(
                  destination.upstreamPoolId(), poolById, targetsByPool, healthPolicyById)));
    }
    String fingerprint = TrafficSplitPolicyFingerprint.compute(policy, sortedDestinations);
    return new CompiledTrafficSplitSection(
        policy.id(),
        policy.selectionKey(),
        policy.selectionKeyName(),
        policy.fallbackMode(),
        fingerprint,
        compiledDestinations);
  }

  private static CompiledUpstreamPoolSection compilePool(
      UUID poolId,
      Map<UUID, UpstreamPoolEntity> poolById,
      Map<UUID, List<UpstreamTargetEntity>> targetsByPool,
      Map<UUID, BackendHealthPolicyEntity> healthPolicyById) {
    UpstreamPoolEntity pool = poolById.get(poolId);
    List<UpstreamTargetEntity> poolTargets =
        targetsByPool.getOrDefault(poolId, List.of()).stream()
            .filter(UpstreamTargetEntity::enabled)
            .sorted(Comparator.comparing(UpstreamTargetEntity::id))
            .toList();
    List<CompiledUpstreamTargetSection> compiledTargets =
        poolTargets.stream()
            .map(t -> new CompiledUpstreamTargetSection(t.id(), t.url(), t.weight()))
            .toList();
    CompiledBackendHealthSection backendHealth = null;
    if (pool.backendHealthPolicyId() != null) {
      BackendHealthPolicyEntity healthPolicy = healthPolicyById.get(pool.backendHealthPolicyId());
      if (healthPolicy != null && healthPolicy.enabled()) {
        backendHealth =
            new CompiledBackendHealthSection(
                healthPolicy.consecutiveFailureThreshold(),
                healthPolicy.ejectionDurationSeconds(),
                healthPolicy.maxEjectionPercent());
      }
    }
    return new CompiledUpstreamPoolSection(
        pool.id(), pool.loadBalancing(), backendHealth, compiledTargets);
  }

  public static List<CompiledApiKeySection> compileApiKeys(
      List<ApiKeyEntity> apiKeys, OffsetDateTime publishInstant) {
    OffsetDateTime now =
        publishInstant == null ? OffsetDateTime.now(ZoneOffset.UTC) : publishInstant;
    return apiKeys.stream()
        .filter(key -> isPublishableAt(key, now))
        .sorted(Comparator.comparing(ApiKeyEntity::keyId))
        .map(RuntimeConfigCompiler::toCompiledApiKey)
        .toList();
  }

  public static boolean isPublishableAt(ApiKeyEntity key, OffsetDateTime instant) {
    if (!key.enabled() || key.revokedAt() != null) {
      return false;
    }
    if (key.expiresAt() != null && !key.expiresAt().isAfter(instant)) {
      return false;
    }
    return key.secretDigest() != null
        && key.secretDigest().length == ApiKeyDigestService.DIGEST_LENGTH_BYTES;
  }

  private static CompiledApiKeySection toCompiledApiKey(ApiKeyEntity key) {
    return new CompiledApiKeySection(
        key.keyId(),
        HexFormat.of().formatHex(key.secretDigest()),
        true,
        key.expiresAt() == null ? null : key.expiresAt().toString());
  }

  public static StoredRuntimeSnapshot toStoredSnapshot(
      HashableRuntimePayload payload, long version, String contentHash) {
    return new StoredRuntimeSnapshot(
        payload.apiId(),
        version,
        contentHash,
        payload.gateway(),
        payload.routes(),
        payload.apiKeys());
  }

  private static Comparator<RouteEntity> routeComparator() {
    return Comparator.comparing((RouteEntity r) -> r.host().toLowerCase(Locale.ROOT))
        .thenComparing((RouteEntity r) -> -normalizedPrefixLength(r.pathPrefix()))
        .thenComparing(RouteEntity::pathPrefix)
        .thenComparing(RouteEntity::id);
  }

  private static int normalizedPrefixLength(String prefix) {
    if ("/".equals(prefix)) {
      return 1;
    }
    return prefix.endsWith("/") ? prefix.length() - 1 : prefix.length();
  }

  /** Compiles routes without security bindings for legacy unit tests. */
  public static HashableRuntimePayload compileWithoutSecurity(
      UUID apiId,
      CompiledGatewaySection gateway,
      List<RouteEntity> enabledRoutes,
      Map<UUID, UpstreamPoolEntity> poolById,
      Map<UUID, List<UpstreamTargetEntity>> targetsByPool) {
    return compile(
        apiId,
        gateway,
        enabledRoutes,
        poolById,
        targetsByPool,
        Map.of(),
        Map.of(),
        Map.of(),
        Map.of(),
        Map.of(),
        Map.of(),
        Map.of(),
        List.of(),
        OffsetDateTime.now(ZoneOffset.UTC));
  }
}
