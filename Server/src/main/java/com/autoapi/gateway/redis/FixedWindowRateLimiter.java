package com.autoapi.gateway.redis;

import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import reactor.core.publisher.Mono;

public class FixedWindowRateLimiter {

  static final RedisScript<List> SCRIPT =
      RedisScript.of(
          """
          local current = redis.call('INCR', KEYS[1])
          if current == 1 then
            redis.call('EXPIRE', KEYS[1], ARGV[1])
          end
          local ttl = redis.call('TTL', KEYS[1])
          return {current, ttl}
          """,
          List.class);

  private final ReactiveStringRedisTemplate redisTemplate;

  public FixedWindowRateLimiter(ReactiveStringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  public Mono<RateLimitResult> increment(String redisKey, int windowSeconds) {
    if (redisKey == null || redisKey.isBlank()) {
      return Mono.error(new IllegalArgumentException("redisKey is required"));
    }
    if (windowSeconds <= 0) {
      return Mono.error(new IllegalArgumentException("windowSeconds must be positive"));
    }
    int ttlSeconds = windowSeconds + 5;
    return redisTemplate
        .execute(SCRIPT, List.of(redisKey), List.of(String.valueOf(ttlSeconds)))
        .next()
        .map(
            values -> {
              long count = ((Number) values.get(0)).longValue();
              long ttl = ((Number) values.get(1)).longValue();
              return new RateLimitResult(count, ttl);
            });
  }

  public record RateLimitResult(long count, long ttlSeconds) {

    public Duration ttlDuration() {
      return Duration.ofSeconds(Math.max(ttlSeconds, 0));
    }
  }
}
