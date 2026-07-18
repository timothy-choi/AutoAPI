package com.autoapi.controlplane.events.webhooks;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.autoapi.controlplane.api.ControlPlaneException;
import org.junit.jupiter.api.Test;

class WebhookUrlValidatorTest {

  @Test
  void rejectsEmbeddedCredentials() {
    WebhooksProperties.Security security =
        new WebhooksProperties.Security("", true, false, false, false);
    assertThrows(
        ControlPlaneException.class,
        () -> WebhookUrlValidator.validate("https://user:pass@example.com/hook", security));
  }

  @Test
  void allowsLoopbackWhenConfigured() {
    WebhooksProperties.Security security =
        new WebhooksProperties.Security("", false, false, true, false);
    assertDoesNotThrow(() -> WebhookUrlValidator.validate("http://127.0.0.1:9090/hook", security));
  }
}
