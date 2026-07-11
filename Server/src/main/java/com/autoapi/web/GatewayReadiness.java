package com.autoapi.web;

import com.autoapi.controlplane.DatabaseReadinessChecker;
import java.util.Optional;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class GatewayReadiness {

  private final Optional<DatabaseReadinessChecker> databaseReadinessChecker;
  private volatile boolean applicationReady;
  private volatile boolean databaseReady = true;

  public GatewayReadiness(Optional<DatabaseReadinessChecker> databaseReadinessChecker) {
    this.databaseReadinessChecker = databaseReadinessChecker;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void onReady() {
    applicationReady = true;
    databaseReadinessChecker.ifPresent(
        checker ->
            checker
                .isDatabaseReady()
                .subscribe(ready -> databaseReady = ready, error -> databaseReady = false));
  }

  public boolean isReady() {
    return applicationReady && databaseReady;
  }
}
