package com.autoapi.controlplane.events;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.autoapi.controlplane.events.webhooks.WebhookEventFilterMatcher;
import com.autoapi.controlplane.persistence.PlatformEventEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WebhookEventFilterMatcherTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void matchesExactAndWildcardFilters() {
    PlatformEventEntity event = event("service.instance.stale.v1", "SERVICE_INSTANCE");
    assertTrue(
        WebhookEventFilterMatcher.matches(event, "[\"service.instance.*\"]", "[]", objectMapper));
    assertFalse(
        WebhookEventFilterMatcher.matches(event, "[\"project.created.v1\"]", "[]", objectMapper));
  }

  @Test
  void nonDeliverableTypesExcludedFromDefaultFanoutSet() {
    assertTrue(
        PlatformEventTypes.NON_DELIVERABLE_EVENT_TYPES.contains(
            PlatformEventTypes.WEBHOOK_DELIVERY_SUCCEEDED));
  }

  private static PlatformEventEntity event(String type, String resourceType) {
    OffsetDateTime now = OffsetDateTime.now();
    return new PlatformEventEntity(
        UUID.randomUUID(),
        1L,
        type,
        1,
        UUID.randomUUID(),
        null,
        resourceType,
        "id-1",
        "SYSTEM",
        "worker",
        "SYSTEM",
        "corr",
        null,
        now,
        now,
        "{}",
        "{}",
        "PENDING",
        now);
  }
}
