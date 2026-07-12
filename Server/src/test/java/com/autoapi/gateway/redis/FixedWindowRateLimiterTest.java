package com.autoapi.gateway.redis;

import static org.junit.jupiter.api.Assertions.*;

import com.autoapi.support.RedisDynamicProperties;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class FixedWindowRateLimiterTest implements RedisDynamicProperties {

  private static ReactiveStringRedisTemplate redisTemplate;
  private static FixedWindowRateLimiter limiter;

  @BeforeAll
  static void setUpRedis() {
    RedisDynamicProperties.startRedisIfNeeded();
    LettuceConnectionFactory factory =
        new LettuceConnectionFactory(REDIS.getHost(), REDIS.getFirstMappedPort());
    factory.afterPropertiesSet();
    redisTemplate = new ReactiveStringRedisTemplate(factory);
    limiter = new FixedWindowRateLimiter(redisTemplate);
  }

  @Test
  void firstIncrementSetsTtl() {
    String key = "test:ratelimit:first:" + System.nanoTime();
    StepVerifier.create(limiter.increment(key, 10))
        .assertNext(
            result -> {
              assertEquals(1, result.count());
              assertTrue(result.ttlSeconds() > 0);
            })
        .verifyComplete();
  }

  @Test
  void incrementsAtomically() {
    String key = "test:ratelimit:atomic:" + System.nanoTime();
    StepVerifier.create(limiter.increment(key, 10).then(limiter.increment(key, 10)))
        .assertNext(result -> assertEquals(2, result.count()))
        .verifyComplete();
  }

  @Test
  void concurrentIncrementsAggregate() {
    String key = "test:ratelimit:concurrent:" + System.nanoTime();
    int workers = 20;
    List<Mono<Long>> tasks = new ArrayList<>();
    for (int i = 0; i < workers; i++) {
      tasks.add(limiter.increment(key, 10).map(FixedWindowRateLimiter.RateLimitResult::count));
    }
    StepVerifier.create(Flux.merge(tasks).collectList())
        .assertNext(
            counts -> {
              assertEquals(workers, counts.size());
              assertEquals(workers, counts.stream().mapToLong(Long::longValue).max().orElse(0));
            })
        .verifyComplete();
  }

  @Test
  void rejectsInvalidArguments() {
    StepVerifier.create(limiter.increment("", 10))
        .expectError(IllegalArgumentException.class)
        .verify();
    StepVerifier.create(limiter.increment("valid-key", 0))
        .expectError(IllegalArgumentException.class)
        .verify();
  }

  @Test
  void windowExpires() throws InterruptedException {
    String key = "test:ratelimit:expire:" + System.nanoTime();
    StepVerifier.create(limiter.increment(key, 2)).expectNextCount(1).verifyComplete();
    Thread.sleep(Duration.ofSeconds(3).toMillis());
    StepVerifier.create(limiter.increment(key, 2))
        .assertNext(result -> assertEquals(1, result.count()))
        .verifyComplete();
  }
}
