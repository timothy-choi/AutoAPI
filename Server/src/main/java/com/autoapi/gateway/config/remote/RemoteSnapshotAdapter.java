package com.autoapi.gateway.config.remote;

import com.autoapi.config.BackendHealthPolicyConfig;
import com.autoapi.config.GatewayConfig;
import com.autoapi.config.RouteConfig;
import com.autoapi.config.RuntimeApiKey;
import com.autoapi.config.RuntimeAuthentication;
import com.autoapi.config.RuntimeConfig;
import com.autoapi.config.RuntimeRateLimit;
import com.autoapi.config.RuntimeRetryPolicyConfig;
import com.autoapi.config.RuntimeTrafficSplitConfig;
import com.autoapi.config.RuntimeTrafficSplitDestination;
import com.autoapi.config.UpstreamConfig;
import com.autoapi.config.UpstreamTargetReference;
import com.autoapi.controlplane.configversion.CompiledRateLimitSection;
import com.autoapi.controlplane.configversion.CompiledRouteSection;
import com.autoapi.controlplane.configversion.CompiledTrafficSplitDestinationSection;
import com.autoapi.controlplane.configversion.CompiledTrafficSplitSection;
import com.autoapi.controlplane.configversion.CompiledUpstreamTargetSection;
import com.autoapi.controlplane.configversion.HashableRuntimePayload;
import com.autoapi.controlplane.configversion.RuntimeContentHasher;
import com.autoapi.controlplane.configversion.StoredRuntimeSnapshot;
import com.autoapi.gateway.config.ActiveRuntimeBundle;
import com.autoapi.validation.UpstreamUriValidator;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpMethod;

public final class RemoteSnapshotAdapter {

  private RemoteSnapshotAdapter() {}

  public static ActiveRuntimeBundle toActiveBundle(
      StoredRuntimeSnapshot snapshot, UUID expectedApiId) {
    validateSnapshot(snapshot, expectedApiId);
    verifyContentHash(snapshot);
    List<RouteConfig> routes = new ArrayList<>();
    for (CompiledRouteSection route : snapshot.routes()) {
      routes.add(toRouteConfig(route));
    }
    Map<String, RuntimeApiKey> apiKeys = toApiKeyMap(snapshot.apiKeys());
    RuntimeConfig runtimeConfig =
        new RuntimeConfig(
            new GatewayConfig(snapshot.gateway().listenAddress(), snapshot.gateway().port()),
            routes,
            apiKeys);
    return new ActiveRuntimeBundle(
        snapshot.apiId(), snapshot.version(), snapshot.contentHash(), runtimeConfig);
  }

  private static void validateSnapshot(StoredRuntimeSnapshot snapshot, UUID expectedApiId) {
    if (snapshot.apiId() == null) {
      throw new RemoteSnapshotValidationException("Snapshot apiId is required");
    }
    if (expectedApiId != null && !expectedApiId.equals(snapshot.apiId())) {
      throw new RemoteSnapshotValidationException("Snapshot apiId does not match configured API");
    }
    if (snapshot.version() < 1) {
      throw new RemoteSnapshotValidationException("Snapshot version must be positive");
    }
    if (snapshot.contentHash() == null || snapshot.contentHash().isBlank()) {
      throw new RemoteSnapshotValidationException("Snapshot contentHash is required");
    }
    if (snapshot.gateway() == null) {
      throw new RemoteSnapshotValidationException("Snapshot gateway section is required");
    }
    if (snapshot.routes() == null || snapshot.routes().isEmpty()) {
      throw new RemoteSnapshotValidationException("Snapshot must contain at least one route");
    }
    if (snapshot.apiKeys() == null) {
      throw new RemoteSnapshotValidationException("Snapshot apiKeys section is required");
    }
  }

  private static void verifyContentHash(StoredRuntimeSnapshot snapshot) {
    String payloadHash =
        RuntimeContentHasher.sha256Hex(
            RuntimeContentHasher.canonicalJson(
                new HashableRuntimePayload(
                    snapshot.apiId(), snapshot.gateway(), snapshot.routes(), snapshot.apiKeys())));
    if (!payloadHash.equals(snapshot.contentHash())) {
      throw new RemoteSnapshotValidationException("Snapshot content hash mismatch");
    }
  }

  private static Map<String, RuntimeApiKey> toApiKeyMap(
      List<com.autoapi.controlplane.configversion.CompiledApiKeySection> apiKeys) {
    Map<String, RuntimeApiKey> map = new LinkedHashMap<>();
    for (var key : apiKeys) {
      map.put(
          key.keyId(),
          new RuntimeApiKey(
              key.keyId(),
              HexFormat.of().parseHex(key.secretDigest()),
              key.enabled(),
              key.expiresAt() == null ? null : Instant.parse(key.expiresAt())));
    }
    return Map.copyOf(map);
  }

  private static RouteConfig toRouteConfig(CompiledRouteSection route) {
    if (route.host() == null || route.host().isBlank()) {
      throw new RemoteSnapshotValidationException("Route host must not be blank");
    }
    if (route.pathPrefix() == null || route.pathPrefix().isBlank()) {
      throw new RemoteSnapshotValidationException("Route pathPrefix must not be blank");
    }
    if (route.methods() == null || route.methods().isEmpty()) {
      throw new RemoteSnapshotValidationException("Route methods must not be empty");
    }
    Set<HttpMethod> methods = new LinkedHashSet<>();
    for (String method : route.methods()) {
      methods.add(HttpMethod.valueOf(method));
    }
    UpstreamConfig upstream = null;
    RuntimeTrafficSplitConfig trafficSplit = null;
    if (route.trafficSplit() != null) {
      trafficSplit = toTrafficSplitConfig(route.trafficSplit());
    } else if (route.upstreamPool() != null) {
      upstream = toUpstreamConfig(route.upstreamPool());
    } else {
      throw new RemoteSnapshotValidationException(
          "Route must define either trafficSplit or upstreamPool");
    }
    RuntimeAuthentication authentication = null;
    if (route.authentication() != null && route.authentication().required()) {
      authentication = new RuntimeAuthentication(true);
    }
    RuntimeRateLimit rateLimit = null;
    if (route.rateLimit() != null) {
      rateLimit = toRuntimeRateLimit(route.rateLimit());
    }
    RuntimeRetryPolicyConfig retry = null;
    if (route.retry() != null) {
      retry = toRuntimeRetry(route.retry());
    }
    return new RouteConfig(
        route.id().toString(),
        route.host(),
        route.pathPrefix(),
        methods,
        upstream,
        trafficSplit,
        authentication,
        rateLimit,
        retry);
  }

  private static RuntimeTrafficSplitConfig toTrafficSplitConfig(
      CompiledTrafficSplitSection section) {
    if (section.destinations() == null || section.destinations().isEmpty()) {
      throw new RemoteSnapshotValidationException("Traffic split must contain destinations");
    }
    List<RuntimeTrafficSplitDestination> destinations = new ArrayList<>();
    int cumulative = 0;
    int totalWeight = 0;
    for (CompiledTrafficSplitDestinationSection destination : section.destinations()) {
      if (destination.weight() > 0) {
        totalWeight += destination.weight();
      }
    }
    for (CompiledTrafficSplitDestinationSection destination : section.destinations()) {
      UpstreamConfig pool = toUpstreamConfig(destination.upstreamPool());
      int start = cumulative;
      int end = cumulative;
      if (destination.weight() > 0) {
        end = cumulative + destination.weight();
        cumulative = end;
      }
      destinations.add(
          new RuntimeTrafficSplitDestination(
              destination.id(),
              destination.name(),
              destination.weight(),
              destination.priority(),
              destination.primary(),
              pool,
              start,
              end));
    }
    if (totalWeight <= 0) {
      throw new RemoteSnapshotValidationException("Traffic split total weight must be positive");
    }
    return new RuntimeTrafficSplitConfig(
        section.policyId(),
        section.selectionKey(),
        section.selectionKeyName(),
        section.fallbackMode(),
        section.fingerprint(),
        totalWeight,
        destinations);
  }

  private static RuntimeRetryPolicyConfig toRuntimeRetry(
      com.autoapi.controlplane.configversion.CompiledRetrySection section) {
    return new RuntimeRetryPolicyConfig(
        section.policyId(),
        section.maxAttempts(),
        section.perAttemptTimeoutMs(),
        section.retryOnConnectFailure(),
        section.retryOnConnectionReset(),
        section.retryOnDnsFailure(),
        section.retryOnResponseTimeout(),
        section.retryableMethods(),
        section.requireIdempotencyKeyForUnsafeMethods(),
        section.budgetPercent(),
        section.budgetMinRetriesPerSecond(),
        section.budgetWindowSeconds());
  }

  private static RuntimeRateLimit toRuntimeRateLimit(CompiledRateLimitSection section) {
    return new RuntimeRateLimit(
        section.policyId(),
        section.limitCount(),
        section.windowSeconds(),
        section.identitySource(),
        section.redisFailureMode());
  }

  private static UpstreamConfig toUpstreamConfig(
      com.autoapi.controlplane.configversion.CompiledUpstreamPoolSection pool) {
    if (pool == null) {
      throw new RemoteSnapshotValidationException("Route upstream pool is required");
    }
    if (!"ROUND_ROBIN".equalsIgnoreCase(pool.algorithm())) {
      throw new RemoteSnapshotValidationException(
          "Unsupported load balancing algorithm: " + pool.algorithm());
    }
    List<UpstreamTargetReference> targets = new ArrayList<>();
    for (CompiledUpstreamTargetSection target : pool.targets()) {
      URI uri = URI.create(target.url());
      UpstreamUriValidator.validate(uri, "compiled target");
      targets.add(new UpstreamTargetReference(target.id(), uri, target.weight()));
    }
    if (targets.isEmpty()) {
      throw new RemoteSnapshotValidationException("Upstream pool must contain enabled targets");
    }
    BackendHealthPolicyConfig health = toBackendHealthPolicy(pool.backendHealth());
    if (targets.size() == 1) {
      UpstreamTargetReference only = targets.getFirst();
      return new UpstreamConfig(pool.id(), only.url(), List.of(only), health);
    }
    return UpstreamConfig.roundRobin(pool.id(), targets, health);
  }

  private static BackendHealthPolicyConfig toBackendHealthPolicy(
      com.autoapi.controlplane.configversion.CompiledBackendHealthSection section) {
    if (section == null) {
      return null;
    }
    return new BackendHealthPolicyConfig(
        section.consecutiveFailureThreshold(),
        section.ejectionDurationSeconds(),
        section.maxEjectionPercent());
  }
}
