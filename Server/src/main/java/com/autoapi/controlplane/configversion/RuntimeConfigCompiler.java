package com.autoapi.controlplane.configversion;

import com.autoapi.controlplane.persistence.ApiKeyEntity;
import com.autoapi.controlplane.persistence.RateLimitPolicyEntity;
import com.autoapi.controlplane.persistence.RouteEntity;
import com.autoapi.controlplane.persistence.RoutePolicyBindingEntity;
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
      List<ApiKeyEntity> apiKeys,
      OffsetDateTime publishInstant) {
    List<CompiledRouteSection> compiledRoutes = new ArrayList<>();
    List<RouteEntity> sortedRoutes = enabledRoutes.stream().sorted(routeComparator()).toList();
    for (RouteEntity route : sortedRoutes) {
      UpstreamPoolEntity pool = poolById.get(route.upstreamPoolId());
      List<UpstreamTargetEntity> poolTargets =
          targetsByPool.getOrDefault(route.upstreamPoolId(), List.of()).stream()
              .filter(UpstreamTargetEntity::enabled)
              .sorted(Comparator.comparing(UpstreamTargetEntity::id))
              .toList();
      List<CompiledUpstreamTargetSection> compiledTargets =
          poolTargets.stream()
              .map(t -> new CompiledUpstreamTargetSection(t.id(), t.url(), t.weight()))
              .toList();
      CompiledUpstreamPoolSection compiledPool =
          new CompiledUpstreamPoolSection(pool.id(), pool.loadBalancing(), compiledTargets);
      List<String> methods =
          java.util.Arrays.stream(route.methods())
              .map(m -> m.toUpperCase(Locale.ROOT))
              .sorted()
              .toList();
      RoutePolicyBindingEntity binding = bindingByRouteId.get(route.id());
      CompiledAuthenticationSection authentication = null;
      CompiledRateLimitSection rateLimit = null;
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
      compiledRoutes.add(
          new CompiledRouteSection(
              route.id(),
              route.host(),
              route.pathPrefix(),
              methods,
              authentication,
              rateLimit,
              compiledPool));
    }
    List<CompiledApiKeySection> compiledKeys = compileApiKeys(apiKeys, publishInstant);
    return new HashableRuntimePayload(apiId, gateway, compiledRoutes, compiledKeys);
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
        List.of(),
        OffsetDateTime.now(ZoneOffset.UTC));
  }
}
