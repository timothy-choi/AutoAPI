package com.autoapi.security;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Computes HMAC-SHA-256 secret digests using the shared gateway pepper. */
public final class ApiKeyDigestService {

  private static final String HMAC_ALGORITHM = "HmacSHA256";
  public static final int DIGEST_LENGTH_BYTES = 32;

  private ApiKeyDigestService() {}

  public static byte[] digestSecret(String secret, String pepper) {
    try {
      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      mac.init(new SecretKeySpec(pepper.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
      return mac.doFinal(secret.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      throw new IllegalStateException("HMAC-SHA-256 is unavailable", e);
    }
  }

  public static Mac newMac(String pepper) {
    try {
      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      mac.init(new SecretKeySpec(pepper.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
      return mac;
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      throw new IllegalStateException("HMAC-SHA-256 is unavailable", e);
    }
  }

  public static byte[] digestSecretWithMac(Mac mac, String secret) {
    synchronized (mac) {
      mac.reset();
      return mac.doFinal(secret.getBytes(StandardCharsets.UTF_8));
    }
  }

  public static boolean constantTimeEquals(byte[] expected, byte[] actual) {
    if (expected == null || actual == null) {
      return false;
    }
    return MessageDigest.isEqual(expected, actual);
  }
}
