package com.autoapi.middleware;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class RequestIdFilterTest {

  @Test
  void generatesWhenAbsent() {
    assertFalse(RequestIdFilter.resolveRequestId(null).isBlank());
  }

  @Test
  void preservesFirstNonBlank() {
    String id = RequestIdFilter.resolveRequestId(List.of("  ", "abc-123", "ignored"));
    assertEquals("abc-123", id);
  }

  @Test
  void truncatesOversizedValues() {
    String longId = "x".repeat(200);
    assertEquals(128, RequestIdFilter.sanitize(longId).length());
  }
}
