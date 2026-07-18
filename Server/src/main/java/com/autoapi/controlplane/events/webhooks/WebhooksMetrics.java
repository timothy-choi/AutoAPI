package com.autoapi.controlplane.events.webhooks;

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
public class WebhooksMetrics {

  private final Counter deliveriesSucceeded;
  private final Counter deliveriesFailed;
  private final Counter deliveriesRetried;
  private final Counter deliveriesDeadLettered;
  private final AtomicLong queueDepth = new AtomicLong(0);

  public WebhooksMetrics(MeterRegistry meterRegistry) {
    this.deliveriesSucceeded =
        Counter.builder("autoapi_webhook_delivery_attempts_total")
            .tag("result", "succeeded")
            .register(meterRegistry);
    this.deliveriesFailed =
        Counter.builder("autoapi_webhook_delivery_attempts_total")
            .tag("result", "failed")
            .register(meterRegistry);
    this.deliveriesRetried =
        Counter.builder("autoapi_webhook_delivery_retries_total").register(meterRegistry);
    this.deliveriesDeadLettered =
        Counter.builder("autoapi_webhook_delivery_dead_letter_total").register(meterRegistry);
    Gauge.builder("autoapi_webhook_delivery_queue_depth", queueDepth, AtomicLong::get)
        .register(meterRegistry);
  }

  public void recordDeliverySucceeded() {
    deliveriesSucceeded.increment();
  }

  public void recordDeliveryFailed() {
    deliveriesFailed.increment();
  }

  public void recordDeliveryRetry() {
    deliveriesRetried.increment();
  }

  public void recordDeadLetter() {
    deliveriesDeadLettered.increment();
  }

  public void setQueueDepth(long depth) {
    queueDepth.set(depth);
  }
}
