package com.autoapi.controlplane.discovery;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "autoapi.discovery")
public record DiscoveryProperties(
    @DefaultValue("true") boolean enabled,
    @DefaultValue("10s") Duration heartbeatInterval,
    @DefaultValue("30s") Duration defaultLeaseDuration,
    @DefaultValue("5s") Duration minLeaseDuration,
    @DefaultValue("5m") Duration maxLeaseDuration,
    @DefaultValue("5s") Duration staleReaperInterval,
    @DefaultValue("100") int staleReaperBatchSize,
    @DefaultValue("24h") Duration staleRetention,
    @DefaultValue("true") boolean autoActivateOnMembershipChange) {

  public DiscoveryProperties {
    if (staleReaperBatchSize < 1) {
      throw new IllegalArgumentException("staleReaperBatchSize must be positive");
    }
    if (minLeaseDuration.compareTo(maxLeaseDuration) > 0) {
      throw new IllegalArgumentException("minLeaseDuration must not exceed maxLeaseDuration");
    }
  }
}
