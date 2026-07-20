package com.autoapi.controlplane.policy;

import com.autoapi.controlplane.configversion.CompiledCircuitBreakerFailurePredicateSection;
import com.autoapi.controlplane.configversion.CompiledCircuitBreakerSection;
import com.autoapi.controlplane.configversion.CompiledEffectivePolicyRouteSection;
import com.autoapi.controlplane.configversion.CompiledRateLimitSection;
import com.autoapi.controlplane.configversion.CompiledRetrySection;
import com.autoapi.controlplane.configversion.CompiledRouteSection;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Applies centrally resolved effective policies onto compiled gateway routes. Gateways consume the
 * flattened snapshot only; they never resolve inheritance.
 */
@Component
@ConditionalOnProperty(
    name = "autoapi.controlplane.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class EffectivePolicyRuntimeBridge {

  private static final UUID EFFECTIVE_POLICY_ID =
      UUID.fromString("00000000-0000-4000-8000-000000000014");

  public List<CompiledRouteSection> apply(
      List<CompiledRouteSection> routes, Map<UUID, EffectivePolicyDocument> effectiveByRoute) {
    if (effectiveByRoute == null || effectiveByRoute.isEmpty()) {
      return routes;
    }
    List<CompiledRouteSection> merged = new ArrayList<>(routes.size());
    for (CompiledRouteSection route : routes) {
      EffectivePolicyDocument document = effectiveByRoute.get(route.id());
      if (document == null || document.policies().isEmpty()) {
        merged.add(route);
        continue;
      }
      merged.add(overlay(route, document.policies()));
    }
    return List.copyOf(merged);
  }

  public List<CompiledEffectivePolicyRouteSection> toSnapshotSections(
      Map<UUID, EffectivePolicyDocument> effectiveByRoute) {
    if (effectiveByRoute == null || effectiveByRoute.isEmpty()) {
      return List.of();
    }
    List<CompiledEffectivePolicyRouteSection> sections = new ArrayList<>();
    effectiveByRoute.forEach(
        (routeId, document) -> {
          if (document != null && !document.policies().isEmpty()) {
            sections.add(
                new CompiledEffectivePolicyRouteSection(routeId, Map.copyOf(document.policies())));
          }
        });
    return List.copyOf(sections);
  }

  private CompiledRouteSection overlay(CompiledRouteSection route, Map<String, JsonNode> policies) {
    CompiledRateLimitSection rateLimit = route.rateLimit();
    CompiledRetrySection retry = route.retry();
    CompiledCircuitBreakerSection circuitBreaker = route.circuitBreaker();

    JsonNode rateLimitNode = policies.get("rateLimit");
    if (rateLimitNode != null && !rateLimitNode.isNull()) {
      rateLimit = parseRateLimit(rateLimitNode);
    }
    JsonNode retryNode = policies.get("retry");
    if (retryNode != null && !retryNode.isNull()) {
      retry = parseRetry(retryNode);
    }
    JsonNode circuitBreakerNode = policies.get("circuitBreaker");
    if (circuitBreakerNode != null && !circuitBreakerNode.isNull()) {
      circuitBreaker = parseCircuitBreaker(circuitBreakerNode);
    }

    return new CompiledRouteSection(
        route.id(),
        route.host(),
        route.pathPrefix(),
        route.methods(),
        route.authentication(),
        rateLimit,
        retry,
        circuitBreaker,
        route.trafficSplit(),
        route.upstreamPool(),
        route.discoveredService());
  }

  private CompiledRateLimitSection parseRateLimit(JsonNode node) {
    return new CompiledRateLimitSection(
        uuidField(node, "policyId", EFFECTIVE_POLICY_ID),
        intField(node, "limitCount", 100),
        intField(node, "windowSeconds", 60),
        textField(node, "identitySource", "API_KEY"),
        textField(node, "redisFailureMode", "FAIL_OPEN"));
  }

  private CompiledRetrySection parseRetry(JsonNode node) {
    List<String> methods = new ArrayList<>();
    JsonNode methodsNode = node.get("retryableMethods");
    if (methodsNode != null && methodsNode.isArray()) {
      methodsNode.forEach(item -> methods.add(item.asText()));
    }
    if (methods.isEmpty()) {
      methods.addAll(List.of("GET", "HEAD", "OPTIONS"));
    }
    return new CompiledRetrySection(
        uuidField(node, "policyId", EFFECTIVE_POLICY_ID),
        intField(node, "maxAttempts", 3),
        intField(node, "perAttemptTimeoutMs", 1000),
        boolField(node, "retryOnConnectFailure", true),
        boolField(node, "retryOnConnectionReset", true),
        boolField(node, "retryOnDnsFailure", true),
        boolField(node, "retryOnResponseTimeout", false),
        List.copyOf(methods),
        boolField(node, "requireIdempotencyKeyForUnsafeMethods", true),
        intField(node, "budgetPercent", 10),
        intField(node, "budgetMinRetriesPerSecond", 1),
        intField(node, "budgetWindowSeconds", 1));
  }

  private CompiledCircuitBreakerSection parseCircuitBreaker(JsonNode node) {
    JsonNode predicate = node.get("failurePredicate");
    return new CompiledCircuitBreakerSection(
        uuidField(node, "policyId", EFFECTIVE_POLICY_ID),
        intField(node, "failureThreshold", 5),
        intField(node, "rollingWindowSeconds", 30),
        intField(node, "openDurationSeconds", 30),
        intField(node, "halfOpenMaxRequests", 1),
        intField(node, "successThreshold", 1),
        new CompiledCircuitBreakerFailurePredicateSection(
            boolField(predicate, "countHttp5xx", true),
            boolField(predicate, "countConnectFailure", true),
            boolField(predicate, "countConnectTimeout", true),
            boolField(predicate, "countReadTimeout", false),
            boolField(predicate, "countTlsFailure", true),
            boolField(predicate, "countTransportException", true),
            boolField(predicate, "countHttp429", false)));
  }

  private static UUID uuidField(JsonNode node, String field, UUID fallback) {
    JsonNode value = node.get(field);
    if (value == null || value.isNull() || value.asText().isBlank()) {
      return fallback;
    }
    return UUID.fromString(value.asText());
  }

  private static int intField(JsonNode node, String field, int fallback) {
    JsonNode value = node.get(field);
    return value == null || value.isNull() ? fallback : value.asInt(fallback);
  }

  private static boolean boolField(JsonNode node, String field, boolean fallback) {
    if (node == null) {
      return fallback;
    }
    JsonNode value = node.get(field);
    return value == null || value.isNull() ? fallback : value.asBoolean(fallback);
  }

  private static String textField(JsonNode node, String field, String fallback) {
    JsonNode value = node.get(field);
    return value == null || value.isNull() ? fallback : value.asText(fallback);
  }

  public static Map<String, JsonNode> copyPolicies(Map<String, JsonNode> policies) {
    Map<String, JsonNode> copy = new LinkedHashMap<>();
    policies.forEach((key, value) -> copy.put(key, value == null ? null : value.deepCopy()));
    return Map.copyOf(copy);
  }
}
