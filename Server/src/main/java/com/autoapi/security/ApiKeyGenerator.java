package com.autoapi.security;

import java.security.SecureRandom;
import java.util.Base64;

public final class ApiKeyGenerator {

  private static final SecureRandom SECURE_RANDOM = new SecureRandom();
  private static final int KEY_ID_LENGTH = 16;
  private static final int SECRET_BYTES = 32;
  private static final char[] KEY_ID_ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();

  private ApiKeyGenerator() {}

  public static GeneratedApiKeyMaterial generate() {
    String keyId = randomKeyId();
    byte[] secretBytes = new byte[SECRET_BYTES];
    SECURE_RANDOM.nextBytes(secretBytes);
    String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes);
    String keyPrefix = buildKeyPrefix(keyId);
    String plaintextKey = "ak_live_" + keyId + "." + secret;
    return new GeneratedApiKeyMaterial(keyId, secret, keyPrefix, plaintextKey);
  }

  private static String randomKeyId() {
    char[] chars = new char[KEY_ID_LENGTH];
    for (int i = 0; i < KEY_ID_LENGTH; i++) {
      chars[i] = KEY_ID_ALPHABET[SECURE_RANDOM.nextInt(KEY_ID_ALPHABET.length)];
    }
    return new String(chars);
  }

  static String buildKeyPrefix(String keyId) {
    if (keyId.length() <= 8) {
      return "ak_live_" + keyId;
    }
    return "ak_live_" + keyId.substring(0, 8) + "..." + keyId.substring(keyId.length() - 4);
  }

  public record GeneratedApiKeyMaterial(
      String keyId, String secret, String keyPrefix, String plaintextKey) {}
}
