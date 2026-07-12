package com.autoapi.gateway.redis;

import com.autoapi.config.RouteConfig;
import com.autoapi.config.RuntimeRateLimit;
import com.autoapi.gateway.auth.GatewaySecurityMetrics;
import com.autoapi.runtime.AutoApiRole;
import com.autoapi.runtime.ConditionalOnAutoApiRole;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@ConditionalOnAutoApiRole({AutoApiRole.GATEWAY, AutoApiRole.COMBINED})
@org.springframework.boot.autoconfigure.condition.ConditionalOnBean(FixedWindowRateLimiter.class)
public class GatewayRateLimitService {

  private static final Logger log = LoggerFactory.getLogger(GatewayRateLimitService.class);

  private final FixedWindowRateLimiter rateLimiter;
  private final GatewaySecurityMetrics metrics;

  public GatewayRateLimitService(
      FixedWindowRateLimiter rateLimiter, GatewaySecurityMetrics metrics) {
    this.rateLimiter = rateLimiter;
    this.metrics = metrics;
  }

  public Mono<RateLimitDecision> check(UUID apiId, RouteConfig route, String keyId) {
    RuntimeRateLimit policy = route.rateLimit();
    if (policy == null) {
      return Mono.just(RateLimitDecision.notLimited());
    }
    Instant now = Instant.now();
    String identityHash = RateLimitIdentityHasher.hashKeyId(keyId);
    String redisKey =
        RateLimitKeyBuilder.buildKey(
            apiId, policy.policyId(), identityHash, policy.windowSeconds(), now);
    long windowStart = (now.getEpochSecond() / policy.windowSeconds()) * policy.windowSeconds();
    long resetEpoch = windowStart + policy.windowSeconds();
    return rateLimiter
        .increment(redisKey, policy.windowSeconds())
        .map(
            result -> {
              long remaining = Math.max(0, policy.limitCount() - result.count());
              boolean exceeded = result.count() > policy.limitCount();
              return RateLimitDecision.allowed(
                  policy.limitCount(),
                  remaining,
                  resetEpoch,
                  exceeded ? Math.max(1, result.ttlSeconds()) : 0,
                  exceeded);
            })
        .onErrorResume(
            ex -> {
              metrics.recordRateLimitRedisError(route.id(), policy.policyId().toString());
              if (logStateChange(route.id(), policy.redisFailureMode())) {
                log.warn(
                    "Redis rate limit unavailable routeId={} policyId={} mode={}",
                    route.id(),
                    policy.policyId(),
                    policy.redisFailureMode());
              }
              if ("FAIL_CLOSED".equals(policy.redisFailureMode())) {
                return Mono.just(RateLimitDecision.blockOnRedisFailure());
              }
              return Mono.just(RateLimitDecision.bypassOnRedisFailure());
            });
  }

  private static boolean logStateChange(String routeId, String mode) {
    return true;
  }

  public record RateLimitDecision(
      boolean limited,
      boolean exceeded,
      boolean failOpenBypass,
      boolean failClosed,
      int limit,
      long remaining,
      long resetEpochSeconds,
      long retryAfterSeconds) {

    static RateLimitDecision notLimited() {
      return new RateLimitDecision(false, false, false, false, 0, 0, 0, 0);
    }

    static RateLimitDecision allowed(
        int limit,
        long remaining,
        long resetEpochSeconds,
        long retryAfterSeconds,
        boolean exceeded) {
      return new RateLimitDecision(
          true, exceeded, false, false, limit, remaining, resetEpochSeconds, retryAfterSeconds);
    }

    static RateLimitDecision bypassOnRedisFailure() {
      return new RateLimitDecision(true, false, true, false, 0, 0, 0, 0);
    }

    static RateLimitDecision blockOnRedisFailure() {
      return new RateLimitDecision(true, false, false, true, 0, 0, 0, 0);
    }

    public boolean allowed() {
      return limited && !exceeded && !failClosed;
    }
  }
}
