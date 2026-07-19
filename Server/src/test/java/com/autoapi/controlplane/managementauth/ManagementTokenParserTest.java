package com.autoapi.controlplane.managementauth;

import static org.junit.jupiter.api.Assertions.*;

import com.autoapi.controlplane.api.ControlPlaneException;
import org.junit.jupiter.api.Test;

class ManagementTokenParserTest {

  @Test
  void parsesValidToken() {
    ManagementTokenParser.ParsedToken parsed =
        ManagementTokenParser.parse("aat_abc12345_xyz789secret", "aat");
    assertEquals("abc12345", parsed.publicTokenId());
    assertEquals("xyz789secret", parsed.secret());
  }

  @Test
  void rejectsMalformedPrefix() {
    assertThrows(
        ControlPlaneException.class, () -> ManagementTokenParser.parse("ak_live_abc_def", "aat"));
  }

  @Test
  void rejectsOverlongToken() {
    String secret = "x".repeat(ManagementTokenParser.MAX_TOKEN_LENGTH);
    assertThrows(ControlPlaneException.class, () -> ManagementTokenParser.parse(secret, "aat"));
  }
}
