package com.autoapi.controlplane.events.webhooks;

import com.autoapi.controlplane.persistence.PlatformEventEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class WebhookEnvelopeBuilder {

  private WebhookEnvelopeBuilder() {}

  public static byte[] canonicalPayloadBytes(PlatformEventEntity event, ObjectMapper objectMapper)
      throws JsonProcessingException {
    ObjectNode envelope = buildEnvelope(event, objectMapper);
    return objectMapper.writeValueAsBytes(envelope);
  }

  public static ObjectNode buildEnvelope(PlatformEventEntity event, ObjectMapper objectMapper)
      throws JsonProcessingException {
    ObjectNode envelope = objectMapper.createObjectNode();
    envelope.put("id", event.id().toString());
    envelope.put("type", event.eventType());
    envelope.put("version", event.eventVersion());
    envelope.put("occurredAt", event.occurredAt().toInstant().toString());
    if (event.projectId() != null) {
      envelope.put("projectId", event.projectId().toString());
    }
    ObjectNode resource = envelope.putObject("resource");
    resource.put("type", event.resourceType());
    resource.put("id", event.resourceId());
    ObjectNode actor = envelope.putObject("actor");
    actor.put("type", event.actorType());
    actor.put("id", event.actorId());
    if (event.correlationId() != null) {
      envelope.put("correlationId", event.correlationId());
    }
    if (event.causationId() != null) {
      envelope.put("causationId", event.causationId().toString());
    }
    envelope.set("data", objectMapper.readTree(event.payload()));
    return envelope;
  }

  public static String sign(String secret, Instant timestamp, byte[] payloadBytes) {
    String material =
        timestamp.getEpochSecond() + "." + new String(payloadBytes, StandardCharsets.UTF_8);
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      byte[] digest = mac.doFinal(material.getBytes(StandardCharsets.UTF_8));
      return "v1=" + bytesToHex(digest);
    } catch (Exception ex) {
      throw new IllegalStateException("HMAC signing failed", ex);
    }
  }

  public static boolean verify(
      String secret, Instant timestamp, byte[] payloadBytes, String signature) {
    if (signature == null || !signature.startsWith("v1=")) {
      return false;
    }
    String expected = sign(secret, timestamp, payloadBytes);
    return constantTimeEquals(expected, signature);
  }

  public static Map<String, String> deliveryHeaders(
      PlatformEventEntity event,
      java.util.UUID deliveryId,
      int attempt,
      Instant timestamp,
      String signature) {
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("Content-Type", "application/json");
    headers.put("User-Agent", "AutoAPI-Webhooks/1.0");
    headers.put("X-AutoAPI-Event-ID", event.id().toString());
    headers.put("X-AutoAPI-Event-Type", event.eventType());
    headers.put("X-AutoAPI-Delivery-ID", deliveryId.toString());
    headers.put("X-AutoAPI-Timestamp", String.valueOf(timestamp.getEpochSecond()));
    headers.put("X-AutoAPI-Signature", signature);
    headers.put("X-AutoAPI-Attempt", String.valueOf(attempt));
    return headers;
  }

  private static boolean constantTimeEquals(String expected, String actual) {
    if (expected.length() != actual.length()) {
      return false;
    }
    int result = 0;
    for (int i = 0; i < expected.length(); i++) {
      result |= expected.charAt(i) ^ actual.charAt(i);
    }
    return result == 0;
  }

  private static String bytesToHex(byte[] bytes) {
    StringBuilder builder = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) {
      builder.append(String.format("%02x", b));
    }
    return builder.toString();
  }
}
