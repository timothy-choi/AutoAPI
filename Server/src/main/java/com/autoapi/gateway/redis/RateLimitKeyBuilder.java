package com.autoapi.gateway.redis;

import java.time.Instant;
import java.util.UUID;

public final class RateLimitKeyBuilder {

  private RateLimitKeyBuilder() {}

  public static String buildKey(
      UUID apiId, UUID policyId, String identityHash, int windowSeconds, Instant now) {
    long epochSeconds = now.getEpochSecond();
    long windowStart = (epochSeconds / windowSeconds) * windowSeconds;
    return "ratelimit:" + apiId + ":" + policyId + ":" + identityHash + ":" + windowStart;
  }
}
