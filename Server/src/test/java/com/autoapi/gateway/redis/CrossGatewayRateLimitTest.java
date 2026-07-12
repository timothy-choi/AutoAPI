package com.autoapi.gateway.redis;

import static org.junit.jupiter.api.Assertions.*;

import com.autoapi.support.RedisDynamicProperties;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.test.StepVerifier;

class CrossGatewayRateLimitTest implements RedisDynamicProperties {

  private static FixedWindowRateLimiter gatewayA;
  private static FixedWindowRateLimiter gatewayB;

  @BeforeAll
  static void setUp() {
    RedisDynamicProperties.startRedisIfNeeded();
    LettuceConnectionFactory factory =
        new LettuceConnectionFactory(REDIS.getHost(), REDIS.getFirstMappedPort());
    factory.afterPropertiesSet();
    ReactiveStringRedisTemplate template = new ReactiveStringRedisTemplate(factory);
    gatewayA = new FixedWindowRateLimiter(template);
    gatewayB = new FixedWindowRateLimiter(template);
  }

  @Test
  void sharedLimitAcrossTwoGateways() {
    UUID apiId = UUID.randomUUID();
    UUID policyId = UUID.randomUUID();
    String identityHash = RateLimitIdentityHasher.hashKeyId("CLIENTKEY1234567");
    Instant now = Instant.now();
    String redisKey = RateLimitKeyBuilder.buildKey(apiId, policyId, identityHash, 10, now);
    int limit = 5;
    for (int i = 0; i < limit; i++) {
      FixedWindowRateLimiter limiter = i % 2 == 0 ? gatewayA : gatewayB;
      StepVerifier.create(limiter.increment(redisKey, 10))
          .assertNext(result -> assertTrue(result.count() <= limit))
          .verifyComplete();
    }
    StepVerifier.create(gatewayB.increment(redisKey, 10))
        .assertNext(result -> assertEquals(6, result.count()))
        .verifyComplete();
  }

  @Test
  void identitiesDoNotShareQuota() {
    UUID apiId = UUID.randomUUID();
    UUID policyId = UUID.randomUUID();
    Instant now = Instant.now();
    String keyA =
        RateLimitKeyBuilder.buildKey(
            apiId, policyId, RateLimitIdentityHasher.hashKeyId("CLIENTA123456789"), 10, now);
    String keyB =
        RateLimitKeyBuilder.buildKey(
            apiId, policyId, RateLimitIdentityHasher.hashKeyId("CLIENTB123456789"), 10, now);
    StepVerifier.create(gatewayA.increment(keyA, 10)).expectNextCount(1).verifyComplete();
    StepVerifier.create(gatewayB.increment(keyB, 10))
        .assertNext(result -> assertEquals(1, result.count()))
        .verifyComplete();
  }
}
