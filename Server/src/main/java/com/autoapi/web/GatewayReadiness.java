package com.autoapi.web;

import com.autoapi.config.RuntimeConfig;
import com.autoapi.config.RuntimeConfigHolder;
import com.autoapi.controlplane.DatabaseReadinessChecker;
import java.util.Optional;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class GatewayReadiness {

  private final RuntimeConfigHolder runtimeConfigHolder;
  private final Optional<DatabaseReadinessChecker> databaseReadinessChecker;
  private volatile boolean applicationReady;

  public GatewayReadiness(
      RuntimeConfigHolder runtimeConfigHolder,
      Optional<DatabaseReadinessChecker> databaseReadinessChecker) {
    this.runtimeConfigHolder = runtimeConfigHolder;
    this.databaseReadinessChecker = databaseReadinessChecker;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void onReady() {
    applicationReady = true;
  }

  public Mono<Boolean> isReady() {
    if (!applicationReady || !localComponentsReady()) {
      return Mono.just(false);
    }
    return databaseReadinessChecker
        .map(checker -> checker.isDatabaseReady().defaultIfEmpty(false).onErrorReturn(false))
        .orElse(Mono.just(true));
  }

  private boolean localComponentsReady() {
    RuntimeConfig config = runtimeConfigHolder.config();
    return config != null && config.gateway() != null && config.routes() != null;
  }
}
