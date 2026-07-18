package com.autoapi.controlplane.events.webhooks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class WebhookSecretCryptoTest {

  @Test
  void encryptsAndDecryptsSecret() {
    byte[] key = new byte[32];
    for (int i = 0; i < key.length; i++) {
      key[i] = (byte) i;
    }
    WebhookSecretCrypto crypto = new WebhookSecretCrypto(Base64.getEncoder().encodeToString(key));
    String secret = "whsec_test_value";
    byte[] encrypted = crypto.encrypt(secret);
    assertNotEquals(secret, new String(encrypted));
    assertEquals(secret, crypto.decrypt(encrypted));
  }
}
