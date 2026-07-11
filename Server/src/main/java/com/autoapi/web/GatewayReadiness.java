package com.autoapi.web;

import com.autoapi.controlplane.DatabaseReadinessChecker;
import java.time.Duration;
import java.util.Optional;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class GatewayReadiness {

  private final Optional<DatabaseReadinessChecker> databaseReadinessChecker;
  private volatile boolean applicationReady;

  public GatewayReadiness(Optional<DatabaseReadinessChecker> databaseReadinessChecker) {
    this.databaseReadinessChecker = databaseReadinessChecker;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void onReady() {
    applicationReady = true;
  }

  public boolean isReady() {
    if (!applicationReady) {
      return false;
    }
    return databaseReadinessChecker
        .map(
            checker ->
                checker
                    .isDatabaseReady()
                    .onErrorReturn(false)
                    .blockOptional(Duration.ofSeconds(2))
                    .orElse(false))
        .orElse(true);
  }
}
