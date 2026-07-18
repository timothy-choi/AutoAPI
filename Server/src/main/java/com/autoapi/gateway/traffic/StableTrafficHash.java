package com.autoapi.gateway.traffic;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Stable cross-process SHA-256 hashing for deterministic traffic-split assignment. */
public final class StableTrafficHash {

  private StableTrafficHash() {}

  public static long nonNegativeBucket(String material) {
    byte[] digest = sha256(material);
    long value = 0L;
    for (int i = 0; i < 8; i++) {
      value = (value << 8) | (digest[i] & 0xFFL);
    }
    return Long.remainderUnsigned(value, Long.MAX_VALUE);
  }

  public static int bucketModTotal(long bucket, int totalWeight) {
    if (totalWeight <= 0) {
      throw new IllegalArgumentException("totalWeight must be positive");
    }
    return (int) Long.remainderUnsigned(bucket, totalWeight);
  }

  public static String hashMaterial(
      String routeId, String policyId, String fingerprint, String selectionKeyValue) {
    return routeId + '|' + policyId + '|' + fingerprint + '|' + selectionKeyValue;
  }

  private static byte[] sha256(String material) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return digest.digest(material.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is unavailable", ex);
    }
  }
}
