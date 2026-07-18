package com.autoapi.controlplane.events.webhooks;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "autoapi.webhooks")
public record WebhooksProperties(
    @DefaultValue("true") boolean enabled,
    @DefaultValue("8") int defaultMaxAttempts,
    @DefaultValue("5s") Duration defaultTimeout,
    @DefaultValue("1s") Duration defaultInitialBackoff,
    @DefaultValue("5m") Duration defaultMaxBackoff,
    @DefaultValue("500ms") Duration workerPollInterval,
    @DefaultValue("50") int workerBatchSize,
    @DefaultValue("8") int workerConcurrency,
    @DefaultValue("65536") int maxPayloadBytes,
    @DefaultValue("1024") int maxResponsePreviewBytes,
    @DefaultValue("32") int maxEventFilters,
    @DefaultValue("20") int maxSubscriptionsPerProject,
    Security security,
    Retention retention) {

  public WebhooksProperties {
    if (workerBatchSize < 1) {
      throw new IllegalArgumentException("workerBatchSize must be positive");
    }
    if (workerConcurrency < 1) {
      throw new IllegalArgumentException("workerConcurrency must be positive");
    }
    if (security == null) {
      security = new Security("", true, false, false, false);
    }
    if (retention == null) {
      retention = new Retention(Duration.ofDays(30), Duration.ofDays(30));
    }
  }

  public record Security(
      String masterKey,
      @DefaultValue("true") boolean requireHttps,
      @DefaultValue("false") boolean allowPrivateAddresses,
      @DefaultValue("false") boolean allowLoopback,
      @DefaultValue("false") boolean followRedirects) {}

  public record Retention(
      @DefaultValue("30d") Duration deliveries, @DefaultValue("30d") Duration attempts) {}
}
