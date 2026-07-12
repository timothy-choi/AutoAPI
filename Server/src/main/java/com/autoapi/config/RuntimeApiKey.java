package com.autoapi.config;

import java.time.Instant;

public record RuntimeApiKey(
    String keyId, byte[] secretDigest, boolean enabled, Instant expiresAt) {}
