package com.autoapi.gateway.config.remote;

import com.autoapi.gateway.GatewayProperties;
import com.autoapi.gateway.config.GatewayActivationAttempt;
import com.autoapi.gateway.config.remote.ControlPlaneGatewayClient.ConfigStatusPayload;
import com.autoapi.runtime.AutoApiRole;
import com.autoapi.runtime.ConditionalOnAutoApiRole;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@ConditionalOnAutoApiRole({AutoApiRole.GATEWAY, AutoApiRole.COMBINED})
@ConditionalOnProperty(name = "autoapi.gateway.config-source", havingValue = "control-plane")
public class ConfigStatusReporter {

  private static final Logger log = LoggerFactory.getLogger(ConfigStatusReporter.class);
  private static final Duration MAX_BACKOFF = Duration.ofSeconds(30);

  private final ControlPlaneGatewayClient gatewayClient;
  private final GatewayProperties gatewayProperties;
  private final AtomicReference<PendingReport> pendingReport = new AtomicReference<>();
  private final AtomicBoolean deliveryInProgress = new AtomicBoolean(false);
  private Disposable retrySubscription;

  public ConfigStatusReporter(
      ControlPlaneGatewayClient gatewayClient, GatewayProperties gatewayProperties) {
    this.gatewayClient = gatewayClient;
    this.gatewayProperties = gatewayProperties;
  }

  public void submit(GatewayActivationAttempt attempt) {
    UUID reportId = UUID.randomUUID();
    PendingReport report =
        new PendingReport(
            reportId,
            ConfigStatusPayload.fromAttempt(reportId, gatewayProperties.apiId(), attempt),
            0,
            0L);
    pendingReport.set(report);
    log.info(
        "Queued config status report gatewayId={} apiId={} version={} status={} reportId={}",
        gatewayProperties.gatewayId(),
        gatewayProperties.apiId(),
        attempt.version(),
        attempt.success() ? "ACK" : "NACK",
        reportId);
  }

  @jakarta.annotation.PostConstruct
  public void startRetryLoop() {
    retrySubscription =
        Flux.interval(Duration.ofSeconds(1)).concatMap(tick -> deliverPendingIfReady()).subscribe();
  }

  private Mono<Void> deliverPendingIfReady() {
    PendingReport report = pendingReport.get();
    if (report == null) {
      return Mono.empty();
    }
    long now = System.currentTimeMillis();
    if (now < report.nextAttemptAtMillis()) {
      return Mono.empty();
    }
    if (!deliveryInProgress.compareAndSet(false, true)) {
      return Mono.empty();
    }
    return gatewayClient
        .reportConfigStatus(report.payload())
        .doOnSuccess(
            ignored -> {
              PendingReport current = pendingReport.get();
              if (current != null && current.reportId().equals(report.reportId())) {
                pendingReport.set(null);
              }
              log.info(
                  "Config status report accepted gatewayId={} reportId={} status={}",
                  gatewayProperties.gatewayId(),
                  report.reportId(),
                  report.payload().status());
            })
        .onErrorResume(
            ControlPlaneConfigClientException.class,
            ex -> {
              int nextAttempt = report.attempts() + 1;
              long backoffMillis =
                  Math.min(
                      MAX_BACKOFF.toMillis(), (long) Math.pow(2, Math.min(nextAttempt, 5)) * 1000L);
              pendingReport.set(
                  new PendingReport(
                      report.reportId(),
                      report.payload(),
                      nextAttempt,
                      System.currentTimeMillis() + backoffMillis));
              if (nextAttempt == 1 || nextAttempt % 5 == 0) {
                log.warn(
                    "Config status report delivery failed gatewayId={} reportId={} attempt={}"
                        + " message={}",
                    gatewayProperties.gatewayId(),
                    report.reportId(),
                    nextAttempt,
                    ex.getMessage());
              }
              return Mono.empty();
            })
        .doFinally(signal -> deliveryInProgress.set(false))
        .then();
  }

  @PreDestroy
  public void shutdown() {
    if (retrySubscription != null) {
      retrySubscription.dispose();
    }
  }

  private record PendingReport(
      UUID reportId, ConfigStatusPayload payload, int attempts, long nextAttemptAtMillis) {}
}
