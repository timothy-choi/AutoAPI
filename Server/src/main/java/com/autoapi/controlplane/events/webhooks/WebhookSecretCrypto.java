package com.autoapi.controlplane.events.webhooks;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** AES-256-GCM encryption for webhook signing secrets at rest. */
public final class WebhookSecretCrypto {

  private static final String ALGORITHM = "AES/GCM/NoPadding";
  private static final int IV_LENGTH_BYTES = 12;
  private static final int TAG_LENGTH_BITS = 128;
  private static final int SECRET_BYTES = 32;

  private final SecretKey masterKey;
  private final SecureRandom secureRandom = new SecureRandom();

  public WebhookSecretCrypto(String masterKeyBase64) {
    if (masterKeyBase64 == null || masterKeyBase64.isBlank()) {
      throw new IllegalStateException("Webhook secret master key is not configured");
    }
    byte[] keyBytes = Base64.getDecoder().decode(masterKeyBase64.trim());
    if (keyBytes.length != 32) {
      throw new IllegalStateException("Webhook secret master key must decode to 32 bytes");
    }
    this.masterKey = new SecretKeySpec(keyBytes, "AES");
  }

  public static String generatePlaintextSecret() {
    byte[] secret = new byte[SECRET_BYTES];
    new SecureRandom().nextBytes(secret);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
  }

  public byte[] encrypt(String plaintextSecret) {
    try {
      byte[] iv = new byte[IV_LENGTH_BYTES];
      secureRandom.nextBytes(iv);
      Cipher cipher = Cipher.getInstance(ALGORITHM);
      cipher.init(Cipher.ENCRYPT_MODE, masterKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
      byte[] ciphertext = cipher.doFinal(plaintextSecret.getBytes(StandardCharsets.UTF_8));
      byte[] combined = new byte[iv.length + ciphertext.length];
      System.arraycopy(iv, 0, combined, 0, iv.length);
      System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
      return combined;
    } catch (GeneralSecurityException ex) {
      throw new IllegalStateException("Failed to encrypt webhook secret", ex);
    }
  }

  public String decrypt(byte[] encrypted) {
    try {
      if (encrypted.length <= IV_LENGTH_BYTES) {
        throw new IllegalArgumentException("Encrypted secret is too short");
      }
      byte[] iv = new byte[IV_LENGTH_BYTES];
      System.arraycopy(encrypted, 0, iv, 0, IV_LENGTH_BYTES);
      byte[] ciphertext = new byte[encrypted.length - IV_LENGTH_BYTES];
      System.arraycopy(encrypted, IV_LENGTH_BYTES, ciphertext, 0, ciphertext.length);
      Cipher cipher = Cipher.getInstance(ALGORITHM);
      cipher.init(Cipher.DECRYPT_MODE, masterKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
      return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
    } catch (GeneralSecurityException ex) {
      throw new IllegalStateException("Failed to decrypt webhook secret", ex);
    }
  }
}
