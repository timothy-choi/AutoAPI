package com.autoapi.controlplane;

import java.time.Duration;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;

/** Blocks startup until PostgreSQL accepts R2DBC connections and Flyway can run. */
@Component
@ConditionalOnProperty(
    name = "autoapi.controlplane.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class ControlPlaneStartupVerifier implements ApplicationRunner {

  private final DatabaseClient databaseClient;

  public ControlPlaneStartupVerifier(DatabaseClient databaseClient) {
    this.databaseClient = databaseClient;
  }

  @Override
  public void run(ApplicationArguments args) {
    for (int attempt = 0; attempt < 60; attempt++) {
      if (Boolean.TRUE.equals(pingDatabase()) && Boolean.TRUE.equals(schemaReady())) {
        return;
      }
      sleepQuietly(500L);
    }
    throw new IllegalStateException(
        "PostgreSQL is not reachable or control-plane schema is missing after startup retries");
  }

  private Boolean schemaReady() {
    return databaseClient
        .sql("SELECT to_regclass('public.projects') AS projects_table")
        .fetch()
        .one()
        .map(row -> row.get("projects_table") != null)
        .defaultIfEmpty(false)
        .onErrorReturn(false)
        .blockOptional(Duration.ofSeconds(5))
        .orElse(false);
  }

  private Boolean pingDatabase() {
    return databaseClient
        .sql("SELECT 1")
        .fetch()
        .one()
        .map(row -> true)
        .defaultIfEmpty(false)
        .onErrorReturn(false)
        .blockOptional(Duration.ofSeconds(5))
        .orElse(false);
  }

  private static void sleepQuietly(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while waiting for PostgreSQL", interrupted);
    }
  }
}
