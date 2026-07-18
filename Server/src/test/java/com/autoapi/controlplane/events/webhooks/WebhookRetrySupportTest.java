package com.autoapi.controlplane.events.webhooks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class WebhookRetrySupportTest {

  @Test
  void classifiesStatusCodes() {
    assertTrue(WebhookRetrySupport.isSuccessStatusCode(200));
    assertFalse(WebhookRetrySupport.isRetryableStatusCode(200));
    assertTrue(WebhookRetrySupport.isRetryableStatusCode(429));
    assertTrue(WebhookRetrySupport.isRetryableStatusCode(503));
    assertFalse(WebhookRetrySupport.isRetryableStatusCode(400));
  }

  @Test
  void computesExponentialBackoff() {
    Duration first =
        WebhookRetrySupport.computeBackoff(1, Duration.ofSeconds(1), Duration.ofMinutes(5));
    Duration second =
        WebhookRetrySupport.computeBackoff(2, Duration.ofSeconds(1), Duration.ofMinutes(5));
    assertEquals(Duration.ofSeconds(1), first);
    assertEquals(Duration.ofSeconds(2), second);
  }

  @Test
  void parsesRetryAfterHeader() {
    assertEquals(
        Duration.ofSeconds(10),
        WebhookRetrySupport.parseRetryAfter("10", Duration.ofMinutes(5)).orElseThrow());
  }
}
