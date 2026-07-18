package com.autoapi.controlplane.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

public record RecordPlatformEventRequest(
    String eventType,
    int eventVersion,
    UUID projectId,
    UUID apiId,
    String resourceType,
    String resourceId,
    EventContext context,
    Map<String, Object> payload,
    Map<String, String> metadata) {

  public static RecordPlatformEventRequest of(
      String eventType,
      UUID projectId,
      UUID apiId,
      String resourceType,
      String resourceId,
      EventContext context,
      Map<String, Object> payload) {
    return new RecordPlatformEventRequest(
        eventType, 1, projectId, apiId, resourceType, resourceId, context, payload, Map.of());
  }

  public void validatePayloadSize(ObjectMapper objectMapper, int maxBytes) {
    try {
      byte[] bytes = objectMapper.writeValueAsBytes(payload == null ? Map.of() : payload);
      if (bytes.length > maxBytes) {
        throw new IllegalArgumentException("Event payload exceeds maximum size");
      }
    } catch (JsonProcessingException ex) {
      throw new IllegalArgumentException("Event payload is not serializable", ex);
    }
  }

  public OffsetDateTime occurredAt() {
    return OffsetDateTime.now(ZoneOffset.UTC);
  }
}
