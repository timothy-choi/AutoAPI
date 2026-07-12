package com.autoapi.support;

import com.redis.testcontainers.RedisContainer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.utility.DockerImageName;

public interface RedisDynamicProperties {

  RedisContainer REDIS =
      new RedisContainer(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

  static void startRedisIfNeeded() {
    if (!REDIS.isRunning()) {
      try {
        REDIS.start();
      } catch (RuntimeException ex) {
        throw new IllegalStateException(
            "Redis Testcontainers tests require a running Docker daemon", ex);
      }
    }
  }

  @DynamicPropertySource
  static void registerRedis(DynamicPropertyRegistry registry) {
    startRedisIfNeeded();
    registry.add(
        "spring.data.redis.url",
        () -> "redis://" + REDIS.getHost() + ":" + REDIS.getFirstMappedPort());
    registry.add("autoapi.security.api-key-pepper", () -> SecurityTestFixtures.TEST_PEPPER);
  }
}
