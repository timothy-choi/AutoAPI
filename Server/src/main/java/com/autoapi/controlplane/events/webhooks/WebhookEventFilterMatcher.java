package com.autoapi.controlplane.events.webhooks;

import com.autoapi.controlplane.persistence.PlatformEventEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;

public final class WebhookEventFilterMatcher {

  private WebhookEventFilterMatcher() {}

  public static boolean matches(
      PlatformEventEntity event,
      String eventFiltersJson,
      String resourceFiltersJson,
      ObjectMapper objectMapper) {
    List<String> eventFilters = readList(eventFiltersJson, objectMapper);
    List<String> resourceFilters = readList(resourceFiltersJson, objectMapper);
    if (!eventFilters.isEmpty() && !matchesEventType(event.eventType(), eventFilters)) {
      return false;
    }
    if (!resourceFilters.isEmpty() && !resourceFilters.contains(event.resourceType())) {
      return false;
    }
    return true;
  }

  private static boolean matchesEventType(String eventType, List<String> filters) {
    for (String filter : filters) {
      if (filter.endsWith(".*")) {
        String prefix = filter.substring(0, filter.length() - 2);
        if (eventType.startsWith(prefix)) {
          return true;
        }
      } else if (filter.equals(eventType)) {
        return true;
      }
    }
    return false;
  }

  private static List<String> readList(String json, ObjectMapper objectMapper) {
    if (json == null || json.isBlank()) {
      return List.of();
    }
    try {
      return objectMapper.readValue(json, new TypeReference<>() {});
    } catch (Exception ex) {
      return List.of();
    }
  }
}
