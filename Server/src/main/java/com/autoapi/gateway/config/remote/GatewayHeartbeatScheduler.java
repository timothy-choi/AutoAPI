package com.autoapi.gateway.config.remote;

import com.autoapi.gateway.GatewayProperties;
import com.autoapi.gateway.config.ActiveRuntimeConfigHolder;
import com.autoapi.gateway.observability.GatewayRuntimeStatusBuilder;
import com.autoapi.gateway.observability.GatewayStructuredLogger;
import com.autoapi.runtime.AutoApiRole;
import com.autoapi.runtime.ConditionalOnAutoApiRole;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@ConditionalOnAutoApiRole({AutoApiRole.GATEWAY, AutoApiRole.COMBINED})
@ConditionalOnProperty(name = "autoapi.gateway.config-source", havingValue = "control-plane")
public class GatewayHeartbeatScheduler {

  private static final Logger log = LoggerFactory.getLogger(GatewayHeartbeatScheduler.class);

  private final ControlPlaneGatewayClient gatewayClient;
  private final GatewayRegistrationState registrationState;
  private final ActiveRuntimeConfigHolder activeRuntimeConfigHolder;
  private final GatewayProperties gatewayProperties;
  private final GatewayRuntimeStatusBuilder runtimeStatusBuilder;
  private final ObjectProvider<GatewayStructuredLogger> structuredLoggerProvider;
  private final AtomicBoolean heartbeatInProgress = new AtomicBoolean(false);
  private Disposable heartbeatSubscription;

  public GatewayHeartbeatScheduler(
      ControlPlaneGatewayClient gatewayClient,
      GatewayRegistrationState registrationState,
      ActiveRuntimeConfigHolder activeRuntimeConfigHolder,
      GatewayProperties gatewayProperties,
      GatewayRuntimeStatusBuilder runtimeStatusBuilder,
      ObjectProvider<GatewayStructuredLogger> structuredLoggerProvider) {
    this.gatewayClient = gatewayClient;
    this.registrationState = registrationState;
    this.activeRuntimeConfigHolder = activeRuntimeConfigHolder;
    this.gatewayProperties = gatewayProperties;
    this.runtimeStatusBuilder = runtimeStatusBuilder;
    this.structuredLoggerProvider = structuredLoggerProvider;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void startHeartbeat() {
    heartbeatSubscription =
        Flux.interval(gatewayProperties.heartbeatInterval())
            .concatMap(tick -> sendHeartbeat())
            .subscribe();
  }

  @PreDestroy
  public void stopHeartbeat() {
    if (heartbeatSubscription != null) {
      heartbeatSubscription.dispose();
    }
  }

  private Mono<Void> sendHeartbeat() {
    if (!registrationState.isRegistered()) {
      return Mono.empty();
    }
    if (!heartbeatInProgress.compareAndSet(false, true)) {
      return Mono.empty();
    }
    GatewayRuntimeStatusBuilder.HeartbeatPayload payload = runtimeStatusBuilder.build();
    return gatewayClient
        .heartbeat(payload)
        .onErrorResume(
            ControlPlaneConfigClientException.class,
            ex -> {
              log.warn(
                  "Gateway heartbeat failed gatewayId={} message={}",
                  gatewayProperties.gatewayId(),
                  ex.getMessage());
              structuredLoggerProvider
                  .getIfAvailable()
                  .heartbeatFailed(payload.instanceId(), ex.getMessage());
              return Mono.empty();
            })
        .doFinally(signal -> heartbeatInProgress.set(false))
        .then();
  }
}
