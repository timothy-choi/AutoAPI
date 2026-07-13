package com.autoapi.gateway.retry;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class IdempotencyKeyValidatorTest {

  @Test
  void acceptsValidKeyShape() {
    assertTrue(IdempotencyKeyValidator.isValid("order-123-abc"));
  }

  @Test
  void rejectsBlankOrControlCharacters() {
    assertFalse(IdempotencyKeyValidator.isValid(""));
    assertFalse(IdempotencyKeyValidator.isValid("bad\nkey"));
    assertFalse(IdempotencyKeyValidator.isValid("x".repeat(256)));
  }
}
