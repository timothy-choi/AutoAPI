package com.autoapi.controlplane.events;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = "autoapi.controlplane.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class EventsMetrics {

  private final Counter eventsCreated;
  private final AtomicLong outboxPending = new AtomicLong(0);

  public EventsMetrics(MeterRegistry meterRegistry) {
    this.eventsCreated =
        Counter.builder("autoapi_events_created_total")
            .description("Platform events recorded")
            .register(meterRegistry);
    Gauge.builder("autoapi_events_outbox_pending", outboxPending, AtomicLong::get)
        .description("Pending platform events awaiting webhook dispatch")
        .register(meterRegistry);
  }

  public void recordEventCreated(String eventCategory) {
    eventsCreated.increment();
  }

  public void setOutboxPending(long count) {
    outboxPending.set(count);
  }
}
