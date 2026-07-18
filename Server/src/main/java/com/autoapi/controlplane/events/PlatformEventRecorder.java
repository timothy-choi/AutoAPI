package com.autoapi.controlplane.events;

import com.autoapi.controlplane.persistence.PlatformEventRepositoryCustom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@ConditionalOnProperty(
    name = "autoapi.controlplane.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class PlatformEventRecorder {

  private static final Logger log = LoggerFactory.getLogger(PlatformEventRecorder.class);

  private final PlatformEventRepositoryCustom repository;
  private final EventsProperties properties;
  private final EventsMetrics metrics;
  private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

  public PlatformEventRecorder(
      PlatformEventRepositoryCustom repository,
      EventsProperties properties,
      EventsMetrics metrics,
      com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
    this.repository = repository;
    this.properties = properties;
    this.metrics = metrics;
    this.objectMapper = objectMapper;
  }

  public Mono<java.util.UUID> record(RecordPlatformEventRequest request) {
    if (!properties.enabled()) {
      return Mono.empty();
    }
    request.validatePayloadSize(objectMapper, properties.maxPayloadBytes());
    return repository
        .insert(request)
        .doOnSuccess(
            event -> {
              metrics.recordEventCreated(eventCategory(request.eventType()));
              log.info(
                  "platform_event_recorded eventId={} eventType={} projectId={} resourceType={}"
                      + " resourceId={} correlationId={}",
                  event.id(),
                  event.eventType(),
                  event.projectId(),
                  event.resourceType(),
                  event.resourceId(),
                  event.correlationId());
            })
        .map(com.autoapi.controlplane.persistence.PlatformEventEntity::id);
  }

  private static String eventCategory(String eventType) {
    int dot = eventType.indexOf('.');
    return dot > 0 ? eventType.substring(0, dot) : "unknown";
  }
}
