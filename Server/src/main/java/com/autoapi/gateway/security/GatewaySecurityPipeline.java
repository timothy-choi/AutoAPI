package com.autoapi.gateway.security;

import com.autoapi.config.RouteConfig;
import com.autoapi.config.RuntimeConfig;
import com.autoapi.gateway.auth.ApiKeyAuthenticator;
import com.autoapi.gateway.auth.AuthenticatedApiKey;
import com.autoapi.gateway.auth.GatewaySecurityMetrics;
import com.autoapi.gateway.config.ActiveRuntimeBundle;
import com.autoapi.gateway.redis.GatewayRateLimitService;
import com.autoapi.gateway.redis.GatewayRateLimitService.RateLimitDecision;
import com.autoapi.proxy.GatewayAttributes;
import com.autoapi.runtime.AutoApiRole;
import com.autoapi.runtime.ConditionalOnAutoApiRole;
import com.autoapi.web.ErrorResponseWriter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@ConditionalOnAutoApiRole({AutoApiRole.GATEWAY, AutoApiRole.COMBINED})
@org.springframework.boot.autoconfigure.condition.ConditionalOnBean(ApiKeyAuthenticator.class)
public class GatewaySecurityPipeline implements GatewaySecurityEnforcer {

  private final ApiKeyAuthenticator apiKeyAuthenticator;
  private final ObjectProvider<GatewayRateLimitService> rateLimitServiceProvider;
  private final GatewaySecurityMetrics metrics;
  private final ErrorResponseWriter errorResponseWriter;

  public GatewaySecurityPipeline(
      ApiKeyAuthenticator apiKeyAuthenticator,
      ObjectProvider<GatewayRateLimitService> rateLimitServiceProvider,
      GatewaySecurityMetrics metrics,
      ErrorResponseWriter errorResponseWriter) {
    this.apiKeyAuthenticator = apiKeyAuthenticator;
    this.rateLimitServiceProvider = rateLimitServiceProvider;
    this.metrics = metrics;
    this.errorResponseWriter = errorResponseWriter;
  }

  @Override
  public Mono<Void> enforce(
      ServerWebExchange exchange, ActiveRuntimeBundle bundle, RouteConfig route) {
    RuntimeConfig config = bundle.runtimeConfig();
    if (!route.authenticationRequired()) {
      return Mono.empty();
    }
    metrics.recordAuthAttempt(route.id());
    String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
    Object authResult = apiKeyAuthenticator.authenticate(config, bundle.apiId(), authorization);
    if (authResult instanceof ApiKeyAuthenticator.AuthFailure failure) {
      metrics.recordAuthFailure(route.id(), failure.category().name());
      return errorResponseWriter.invalidApiKey(exchange);
    }
    AuthenticatedApiKey identity = ((ApiKeyAuthenticator.AuthSuccess) authResult).identity();
    exchange.getAttributes().put(GatewayAttributes.AUTHENTICATED_API_KEY, identity);
    if (!route.rateLimitEnabled()) {
      return Mono.empty();
    }
    GatewayRateLimitService rateLimitService = rateLimitServiceProvider.getIfAvailable();
    if (rateLimitService == null) {
      metrics.recordRateLimitRedisError(route.id(), route.rateLimit().policyId().toString());
      if ("FAIL_CLOSED".equals(route.rateLimit().redisFailureMode())) {
        metrics.recordRateLimitFailClosed(route.id(), route.rateLimit().policyId().toString());
        return errorResponseWriter.rateLimitDependencyUnavailable(exchange);
      }
      metrics.recordRateLimitFailOpen(route.id(), route.rateLimit().policyId().toString());
      return Mono.empty();
    }
    return rateLimitService
        .check(bundle.apiId(), route, identity.keyId())
        .flatMap(
            decision -> {
              if (decision.exceeded()) {
                metrics.recordRateLimitRejected(
                    route.id(), route.rateLimit().policyId().toString());
                return errorResponseWriter.rateLimitExceeded(exchange, decision);
              }
              if (decision.allowed()) {
                metrics.recordRateLimitAllowed(route.id(), route.rateLimit().policyId().toString());
                applyRateLimitHeaders(exchange, decision);
              }
              if (decision.failOpenBypass()) {
                metrics.recordRateLimitFailOpen(
                    route.id(), route.rateLimit().policyId().toString());
              }
              if (decision.failClosed()) {
                metrics.recordRateLimitFailClosed(
                    route.id(), route.rateLimit().policyId().toString());
                return errorResponseWriter.rateLimitDependencyUnavailable(exchange);
              }
              return Mono.empty();
            });
  }

  private static void applyRateLimitHeaders(
      ServerWebExchange exchange, RateLimitDecision decision) {
    exchange.getResponse().getHeaders().set("RateLimit-Limit", String.valueOf(decision.limit()));
    exchange
        .getResponse()
        .getHeaders()
        .set("RateLimit-Remaining", String.valueOf(decision.remaining()));
    exchange
        .getResponse()
        .getHeaders()
        .set("RateLimit-Reset", String.valueOf(decision.resetEpochSeconds()));
    if (decision.retryAfterSeconds() > 0) {
      exchange
          .getResponse()
          .getHeaders()
          .set("Retry-After", String.valueOf(decision.retryAfterSeconds()));
    }
  }
}
