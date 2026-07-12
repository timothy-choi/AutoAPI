package com.autoapi.gateway.auth;

import static org.junit.jupiter.api.Assertions.*;

import com.autoapi.config.RuntimeConfig;
import com.autoapi.gateway.auth.ApiKeyAuthenticator.AuthFailure;
import com.autoapi.gateway.auth.ApiKeyAuthenticator.AuthSuccess;
import com.autoapi.gateway.auth.ApiKeyAuthenticator.FailureCategory;
import com.autoapi.security.ApiKeyGenerator;
import com.autoapi.support.SecurityTestFixtures;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ApiKeyAuthenticatorTest {

  private ApiKeyGenerator.GeneratedApiKeyMaterial keyMaterial;
  private RuntimeConfig config;
  private ApiKeyAuthenticator authenticator;

  @BeforeEach
  void setUp() {
    keyMaterial = ApiKeyGenerator.generate();
    config = SecurityTestFixtures.protectedRouteConfig(19080, keyMaterial, false);
    authenticator = new ApiKeyAuthenticator(SecurityTestFixtures.TEST_PEPPER);
  }

  @Test
  void acceptsValidKey() {
    Object result =
        authenticator.authenticate(
            config, UUID.randomUUID(), "Bearer " + keyMaterial.plaintextKey());
    assertInstanceOf(AuthSuccess.class, result);
    assertEquals(keyMaterial.keyId(), ((AuthSuccess) result).identity().keyId());
  }

  @Test
  void rejectsMissingHeader() {
    AuthFailure failure = (AuthFailure) authenticator.authenticate(config, UUID.randomUUID(), null);
    assertEquals(FailureCategory.MISSING_HEADER, failure.category());
  }

  @Test
  void rejectsWrongScheme() {
    AuthFailure failure =
        (AuthFailure)
            authenticator.authenticate(
                config, UUID.randomUUID(), "Basic " + keyMaterial.plaintextKey());
    assertEquals(FailureCategory.INVALID_SCHEME, failure.category());
  }

  @Test
  void rejectsMalformedKey() {
    AuthFailure failure =
        (AuthFailure) authenticator.authenticate(config, UUID.randomUUID(), "Bearer not-a-key");
    assertEquals(FailureCategory.MALFORMED_KEY, failure.category());
  }

  @Test
  void rejectsUnknownKeyId() {
    ApiKeyGenerator.GeneratedApiKeyMaterial other = ApiKeyGenerator.generate();
    AuthFailure failure =
        (AuthFailure)
            authenticator.authenticate(config, UUID.randomUUID(), "Bearer " + other.plaintextKey());
    assertEquals(FailureCategory.UNKNOWN_KEY_ID, failure.category());
  }

  @Test
  void rejectsIncorrectSecret() {
    ApiKeyGenerator.GeneratedApiKeyMaterial wrong = ApiKeyGenerator.generate();
    String token = "ak_live_" + keyMaterial.keyId() + "." + wrong.secret();
    AuthFailure failure =
        (AuthFailure) authenticator.authenticate(config, UUID.randomUUID(), "Bearer " + token);
    assertEquals(FailureCategory.DIGEST_MISMATCH, failure.category());
  }

  @Test
  void rejectsExpiredKey() {
    RuntimeConfig expired =
        SecurityTestFixtures.expiredKeyConfig(
            19080, keyMaterial, Instant.parse("2020-01-01T00:00:00Z"));
    AuthFailure failure =
        (AuthFailure)
            authenticator.authenticate(
                expired, UUID.randomUUID(), "Bearer " + keyMaterial.plaintextKey());
    assertEquals(FailureCategory.EXPIRED_KEY, failure.category());
  }

  @Test
  void lookupIsO1ByKeyId() {
    assertNotNull(config.apiKeysByKeyId().get(keyMaterial.keyId()));
    assertNull(config.apiKeysByKeyId().get("UNKNOWNKEYID1234"));
  }
}
