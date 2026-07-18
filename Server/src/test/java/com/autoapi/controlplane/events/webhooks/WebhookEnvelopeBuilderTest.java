package com.autoapi.controlplane.events.webhooks;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.autoapi.controlplane.persistence.PlatformEventEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WebhookEnvelopeBuilderTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  void signAndVerifyWithFixedTimestamp() throws Exception {
    PlatformEventEntity event = sampleEvent();
    byte[] payload = WebhookEnvelopeBuilder.canonicalPayloadBytes(event, MAPPER);
    String secret = "test-secret-value";
    var timestamp = java.time.Instant.parse("2026-07-18T20:14:31Z");
    String signature = WebhookEnvelopeBuilder.sign(secret, timestamp, payload);
    assertTrue(WebhookEnvelopeBuilder.verify(secret, timestamp, payload, signature));
    assertFalse(WebhookEnvelopeBuilder.verify("wrong", timestamp, payload, signature));
  }

  private static PlatformEventEntity sampleEvent() {
    OffsetDateTime now = OffsetDateTime.parse("2026-07-18T20:14:31Z");
    return new PlatformEventEntity(
        UUID.fromString("11111111-1111-1111-1111-111111111111"),
        1L,
        "project.created.v1",
        1,
        UUID.fromString("22222222-2222-2222-2222-222222222222"),
        null,
        "PROJECT",
        "22222222-2222-2222-2222-222222222222",
        "API_CLIENT",
        "management-api",
        "MANAGEMENT_API",
        "req-1",
        null,
        now,
        now,
        "{\"name\":\"demo\"}",
        "{}",
        "PENDING",
        now);
  }
}
