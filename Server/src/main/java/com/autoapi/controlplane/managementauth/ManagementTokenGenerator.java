package com.autoapi.controlplane.managementauth;

import com.autoapi.security.ApiKeyGenerator;
import java.security.SecureRandom;
import java.util.Base64;

public final class ManagementTokenGenerator {

  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private ManagementTokenGenerator() {}

  public record GeneratedToken(String publicTokenId, String secret, String plaintextToken) {}

  public static GeneratedToken generate(String prefix, int secretBytes) {
    String publicTokenId = ApiKeyGenerator.generate().keyId();
    byte[] secretMaterial = new byte[secretBytes];
    SECURE_RANDOM.nextBytes(secretMaterial);
    String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(secretMaterial);
    String tokenPrefix = prefix == null || prefix.isBlank() ? "aat" : prefix;
    String plaintextToken = tokenPrefix + "_" + publicTokenId + "_" + secret;
    return new GeneratedToken(publicTokenId, secret, plaintextToken);
  }
}
