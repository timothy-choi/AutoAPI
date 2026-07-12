package com.autoapi.gateway.auth;

import com.autoapi.config.RuntimeApiKey;
import com.autoapi.config.RuntimeConfig;
import com.autoapi.security.ApiKeyDigestService;
import com.autoapi.security.StructuredApiKey;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;

public final class ApiKeyAuthenticator {

  public enum FailureCategory {
    MISSING_HEADER,
    INVALID_SCHEME,
    MALFORMED_KEY,
    UNKNOWN_KEY_ID,
    DIGEST_MISMATCH,
    DISABLED_KEY,
    EXPIRED_KEY
  }

  public record AuthSuccess(AuthenticatedApiKey identity) {}

  public record AuthFailure(FailureCategory category) {}

  private final Mac macPrototype;

  public ApiKeyAuthenticator(String pepper) {
    this.macPrototype = ApiKeyDigestService.newMac(pepper);
  }

  public Object authenticate(RuntimeConfig config, UUID apiId, String authorizationHeader) {
    if (authorizationHeader == null || authorizationHeader.isBlank()) {
      return new AuthFailure(FailureCategory.MISSING_HEADER);
    }
    String trimmed = authorizationHeader.trim();
    if (!trimmed.regionMatches(true, 0, "Bearer ", 0, 7)) {
      return new AuthFailure(FailureCategory.INVALID_SCHEME);
    }
    String token = trimmed.substring(7).trim();
    if (token.isEmpty()) {
      return new AuthFailure(FailureCategory.MALFORMED_KEY);
    }
    StructuredApiKey structured;
    try {
      structured = StructuredApiKey.parse(token);
    } catch (StructuredApiKey.ApiKeyFormatException ex) {
      return new AuthFailure(FailureCategory.MALFORMED_KEY);
    }
    Map<String, RuntimeApiKey> keys = config.apiKeysByKeyId();
    RuntimeApiKey runtimeKey = keys.get(structured.keyId());
    if (runtimeKey == null) {
      return new AuthFailure(FailureCategory.UNKNOWN_KEY_ID);
    }
    if (!runtimeKey.enabled()) {
      return new AuthFailure(FailureCategory.DISABLED_KEY);
    }
    if (runtimeKey.expiresAt() != null && !Instant.now().isBefore(runtimeKey.expiresAt())) {
      return new AuthFailure(FailureCategory.EXPIRED_KEY);
    }
    byte[] actualDigest =
        ApiKeyDigestService.digestSecretWithMac(macPrototype, structured.secret());
    if (!ApiKeyDigestService.constantTimeEquals(runtimeKey.secretDigest(), actualDigest)) {
      return new AuthFailure(FailureCategory.DIGEST_MISMATCH);
    }
    return new AuthSuccess(new AuthenticatedApiKey(structured.keyId(), apiId));
  }
}
