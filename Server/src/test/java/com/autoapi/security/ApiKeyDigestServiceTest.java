package com.autoapi.security;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ApiKeyDigestServiceTest {

  @Test
  void digestIsDeterministicAndConstantTime() {
    byte[] first = ApiKeyDigestService.digestSecret("secret-value", SecurityTestSupport.PEPPER);
    byte[] second = ApiKeyDigestService.digestSecret("secret-value", SecurityTestSupport.PEPPER);
    assertArrayEquals(first, second);
    assertEquals(ApiKeyDigestService.DIGEST_LENGTH_BYTES, first.length);
    assertTrue(ApiKeyDigestService.constantTimeEquals(first, second));
    assertFalse(ApiKeyDigestService.constantTimeEquals(first, new byte[32]));
  }

  @Test
  void macDigestMatchesDirectDigest() {
    var mac = ApiKeyDigestService.newMac(SecurityTestSupport.PEPPER);
    byte[] direct = ApiKeyDigestService.digestSecret("abc", SecurityTestSupport.PEPPER);
    byte[] viaMac = ApiKeyDigestService.digestSecretWithMac(mac, "abc");
    assertArrayEquals(direct, viaMac);
  }

  private static final class SecurityTestSupport {
    static final String PEPPER = "development-only-test-pepper-minimum-sixteen-characters";
  }
}
