package com.autoapi.security;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class StructuredApiKeyTest {

  @Test
  void parsesValidKey() {
    ApiKeyGenerator.GeneratedApiKeyMaterial material = ApiKeyGenerator.generate();
    StructuredApiKey parsed = StructuredApiKey.parse(material.plaintextKey());
    assertEquals(material.keyId(), parsed.keyId());
    assertEquals(material.secret(), parsed.secret());
  }

  @Test
  void rejectsMalformedKey() {
    assertThrows(
        StructuredApiKey.ApiKeyFormatException.class, () -> StructuredApiKey.parse("bad-key"));
    assertThrows(
        StructuredApiKey.ApiKeyFormatException.class,
        () -> StructuredApiKey.parse("ak_live_short.bad"));
    assertThrows(StructuredApiKey.ApiKeyFormatException.class, () -> StructuredApiKey.parse(""));
  }
}
