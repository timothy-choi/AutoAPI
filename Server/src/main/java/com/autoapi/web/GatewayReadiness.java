package com.autoapi.web;

import com.autoapi.config.RuntimeConfigHolder;
import com.autoapi.controlplane.ControlPlaneProperties;
import com.autoapi.controlplane.DatabaseReadinessChecker;
import com.autoapi.gateway.config.ActiveRuntimeConfigHolder;
import com.autoapi.runtime.AutoApiRuntimeProperties;
import java.util.Optional;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class GatewayReadiness {

  private final AutoApiRuntimeProperties runtimeProperties;
  private final Optional<ControlPlaneProperties> controlPlaneProperties;
  private final Optional<RuntimeConfigHolder> runtimeConfigHolder;
  private final ActiveRuntimeConfigHolder activeRuntimeConfigHolder;
  private final Optional<DatabaseReadinessChecker> databaseReadinessChecker;
  private volatile boolean applicationReady;

  public GatewayReadiness(
      AutoApiRuntimeProperties runtimeProperties,
      Optional<ControlPlaneProperties> controlPlaneProperties,
      Optional<RuntimeConfigHolder> runtimeConfigHolder,
      ActiveRuntimeConfigHolder activeRuntimeConfigHolder,
      Optional<DatabaseReadinessChecker> databaseReadinessChecker) {
    this.runtimeProperties = runtimeProperties;
    this.controlPlaneProperties = controlPlaneProperties;
    this.runtimeConfigHolder = runtimeConfigHolder;
    this.activeRuntimeConfigHolder = activeRuntimeConfigHolder;
    this.databaseReadinessChecker = databaseReadinessChecker;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void onReady() {
    applicationReady = true;
  }

  public Mono<Boolean> isReady() {
    if (!applicationReady) {
      return Mono.just(false);
    }
    if (runtimeProperties.role().runsGateway()) {
      if (!gatewayComponentsReady()) {
        return Mono.just(false);
      }
    }
    if (requiresDatabase()) {
      if (databaseReadinessChecker.isEmpty()) {
        return Mono.just(false);
      }
      return databaseReadinessChecker
          .get()
          .isDatabaseReady()
          .defaultIfEmpty(false)
          .onErrorReturn(false);
    }
    return Mono.just(true);
  }

  private boolean gatewayComponentsReady() {
    if (!activeRuntimeConfigHolder.hasActiveConfig()) {
      return false;
    }
    var config = activeRuntimeConfigHolder.getActive().runtimeConfig();
    return config != null && config.gateway() != null && config.routes() != null;
  }

  private boolean requiresDatabase() {
    return runtimeProperties.role().runsControlPlane()
        && controlPlaneProperties.map(ControlPlaneProperties::enabled).orElse(false);
  }
}
