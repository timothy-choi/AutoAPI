package com.autoapi.gateway.config.remote;

import com.autoapi.gateway.GatewayProperties;
import com.autoapi.runtime.AutoApiRole;
import com.autoapi.runtime.ConditionalOnAutoApiRole;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class GatewayRegistrationManager {

  private static final Logger log = LoggerFactory.getLogger(GatewayRegistrationManager.class);

  private final ControlPlaneGatewayClient gatewayClient;
  private final GatewayRegistrationState registrationState;
  private final GatewayProperties gatewayProperties;
  private final AtomicBoolean registrationInProgress = new AtomicBoolean(false);
  private Disposable registrationSubscription;

  public GatewayRegistrationManager(
      ControlPlaneGatewayClient gatewayClient,
      GatewayRegistrationState registrationState,
      GatewayProperties gatewayProperties) {
    this.gatewayClient = gatewayClient;
    this.registrationState = registrationState;
    this.gatewayProperties = gatewayProperties;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void startRegistrationLoop() {
    validateGatewayIdentity();
    Duration retryInterval = Duration.ofSeconds(2);
    registrationSubscription =
        Mono.defer(this::attemptRegistration)
            .repeatWhen(completed -> completed.delayElements(retryInterval))
            .subscribe();
  }

  @PreDestroy
  public void stopRegistrationLoop() {
    if (registrationSubscription != null) {
      registrationSubscription.dispose();
    }
  }

  public Mono<Void> awaitRegistered() {
    if (registrationState.isRegistered()) {
      return Mono.empty();
    }
    return Flux.interval(Duration.ofMillis(100))
        .filter(tick -> registrationState.isRegistered())
        .next()
        .then();
  }

  private Mono<Void> attemptRegistration() {
    if (registrationState.isRegistered()) {
      return Mono.empty();
    }
    String gatewayId = gatewayProperties.gatewayId();
    if (gatewayId == null || gatewayId.isBlank()) {
      return Mono.empty();
    }
    if (!registrationInProgress.compareAndSet(false, true)) {
      return Mono.empty();
    }
    return gatewayClient
        .register()
        .doOnSuccess(
            ignored -> {
              registrationState.markRegistered();
              log.info(
                  "Gateway registered gatewayId={} group={}",
                  gatewayProperties.gatewayId(),
                  gatewayProperties.gatewayGroup());
            })
        .onErrorResume(
            ControlPlaneConfigClientException.class,
            ex -> {
              log.warn(
                  "Gateway registration failed gatewayId={} message={}",
                  gatewayProperties.gatewayId(),
                  ex.getMessage());
              return Mono.empty();
            })
        .doFinally(signal -> registrationInProgress.set(false))
        .then();
  }

  private void validateGatewayIdentity() {
    String gatewayId = gatewayProperties.gatewayId();
    if (gatewayId == null || gatewayId.isBlank()) {
      log.warn("Gateway ID not configured; registration deferred until autoapi.gateway.id is set");
      return;
    }
    if (!gatewayId.matches("^[a-zA-Z0-9._-]{1,128}$")) {
      throw new IllegalStateException(
          "autoapi.gateway.id must match [a-zA-Z0-9._-] and be at most 128 characters");
    }
  }
}
