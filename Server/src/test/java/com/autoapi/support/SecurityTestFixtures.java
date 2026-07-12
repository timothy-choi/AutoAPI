package com.autoapi.support;

import com.autoapi.config.GatewayConfig;
import com.autoapi.config.RouteConfig;
import com.autoapi.config.RuntimeApiKey;
import com.autoapi.config.RuntimeAuthentication;
import com.autoapi.config.RuntimeConfig;
import com.autoapi.config.RuntimeRateLimit;
import com.autoapi.config.UpstreamConfig;
import com.autoapi.security.ApiKeyDigestService;
import com.autoapi.security.ApiKeyGenerator;
import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpMethod;

public final class SecurityTestFixtures {

  public static final String TEST_PEPPER =
      "development-only-test-pepper-minimum-sixteen-characters";

  public static final UUID TEST_API_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

  private SecurityTestFixtures() {}

  public static RuntimeConfig protectedRouteConfig(
      int upstreamPort, ApiKeyGenerator.GeneratedApiKeyMaterial key, boolean withRateLimit) {
    return protectedRouteConfig(upstreamPort, key, withRateLimit, 5, 10);
  }

  public static RuntimeConfig protectedRouteConfig(
      int upstreamPort,
      ApiKeyGenerator.GeneratedApiKeyMaterial key,
      boolean withRateLimit,
      int limitCount,
      int windowSeconds) {
    UUID policyId = UUID.fromString("00000000-0000-0000-0000-0000000000f1");
    RuntimeAuthentication authentication = new RuntimeAuthentication(true);
    RuntimeRateLimit rateLimit =
        withRateLimit
            ? new RuntimeRateLimit(policyId, limitCount, windowSeconds, "API_KEY", "FAIL_OPEN")
            : null;
    RouteConfig route =
        new RouteConfig(
            "orders-route",
            "api.autoapi.local",
            "/v1/orders",
            Set.of(HttpMethod.GET),
            new UpstreamConfig(URI.create("http://127.0.0.1:" + upstreamPort)),
            authentication,
            rateLimit);
    byte[] digest = ApiKeyDigestService.digestSecret(key.secret(), TEST_PEPPER);
    Map<String, RuntimeApiKey> keys = new LinkedHashMap<>();
    keys.put(key.keyId(), new RuntimeApiKey(key.keyId(), digest, true, null));
    return new RuntimeConfig(new GatewayConfig("127.0.0.1", 8080), List.of(route), keys);
  }

  public static RuntimeConfig protectedRouteConfigWithFailureMode(
      int upstreamPort,
      ApiKeyGenerator.GeneratedApiKeyMaterial key,
      String redisFailureMode,
      int limit,
      int windowSeconds) {
    UUID policyId = UUID.fromString("00000000-0000-0000-0000-0000000000f2");
    RuntimeAuthentication authentication = new RuntimeAuthentication(true);
    RuntimeRateLimit rateLimit =
        new RuntimeRateLimit(policyId, limit, windowSeconds, "API_KEY", redisFailureMode);
    RouteConfig route =
        new RouteConfig(
            "orders-route",
            "api.autoapi.local",
            "/v1/orders",
            Set.of(HttpMethod.GET),
            new UpstreamConfig(URI.create("http://127.0.0.1:" + upstreamPort)),
            authentication,
            rateLimit);
    byte[] digest = ApiKeyDigestService.digestSecret(key.secret(), TEST_PEPPER);
    Map<String, RuntimeApiKey> keys =
        Map.of(key.keyId(), new RuntimeApiKey(key.keyId(), digest, true, null));
    return new RuntimeConfig(new GatewayConfig("127.0.0.1", 8080), List.of(route), keys);
  }

  public static RuntimeConfig expiredKeyConfig(
      int upstreamPort, ApiKeyGenerator.GeneratedApiKeyMaterial key, Instant expiresAt) {
    RuntimeAuthentication authentication = new RuntimeAuthentication(true);
    RouteConfig route =
        new RouteConfig(
            "orders-route",
            "api.autoapi.local",
            "/v1/orders",
            Set.of(HttpMethod.GET),
            new UpstreamConfig(URI.create("http://127.0.0.1:" + upstreamPort)),
            authentication,
            null);
    byte[] digest = ApiKeyDigestService.digestSecret(key.secret(), TEST_PEPPER);
    Map<String, RuntimeApiKey> keys =
        Map.of(key.keyId(), new RuntimeApiKey(key.keyId(), digest, true, expiresAt));
    return new RuntimeConfig(new GatewayConfig("127.0.0.1", 8080), List.of(route), keys);
  }
}
