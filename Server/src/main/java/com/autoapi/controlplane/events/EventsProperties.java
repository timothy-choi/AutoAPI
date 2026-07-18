package com.autoapi.controlplane.events;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "autoapi.events")
public record EventsProperties(
    @DefaultValue("true") boolean enabled,
    @DefaultValue("65536") int maxPayloadBytes,
    @DefaultValue("500ms") Duration outboxPollInterval,
    @DefaultValue("100") int outboxBatchSize,
    @DefaultValue("2") int outboxWorkerConcurrency,
    @DefaultValue("90d") Duration retention,
    @DefaultValue("1h") Duration cleanupInterval,
    @DefaultValue("1000") int cleanupBatchSize) {

  public EventsProperties {
    if (maxPayloadBytes < 1024) {
      throw new IllegalArgumentException("maxPayloadBytes must be at least 1024");
    }
    if (outboxBatchSize < 1) {
      throw new IllegalArgumentException("outboxBatchSize must be positive");
    }
  }
}
