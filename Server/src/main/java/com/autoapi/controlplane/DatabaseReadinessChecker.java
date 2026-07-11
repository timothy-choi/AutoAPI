package com.autoapi.controlplane;

import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@ConditionalOnProperty(
    name = "autoapi.controlplane.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class DatabaseReadinessChecker {

  private static final Logger log = LoggerFactory.getLogger(DatabaseReadinessChecker.class);
  private static final long FAILURE_LOG_INTERVAL_MS = 30_000L;

  private final DatabaseClient databaseClient;
  private final AtomicLong lastFailureLogEpochMs = new AtomicLong(0);

  public DatabaseReadinessChecker(DatabaseClient databaseClient) {
    this.databaseClient = databaseClient;
  }

  public Mono<Boolean> isDatabaseReady() {
    return databaseClient
        .sql("SELECT 1")
        .map((row, metadata) -> true)
        .one()
        .defaultIfEmpty(false)
        .onErrorResume(
            ex -> {
              logFailure(ex);
              return Mono.just(false);
            });
  }

  private void logFailure(Throwable ex) {
    long now = System.currentTimeMillis();
    long last = lastFailureLogEpochMs.get();
    if (now - last > FAILURE_LOG_INTERVAL_MS && lastFailureLogEpochMs.compareAndSet(last, now)) {
      log.warn("PostgreSQL readiness check failed: {}", ex.toString());
    } else if (log.isDebugEnabled()) {
      log.debug("PostgreSQL readiness check failed", ex);
    }
  }
}
