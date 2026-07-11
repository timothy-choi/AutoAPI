package com.autoapi.controlplane;

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

  private final DatabaseClient databaseClient;

  public DatabaseReadinessChecker(DatabaseClient databaseClient) {
    this.databaseClient = databaseClient;
  }

  public Mono<Boolean> isDatabaseReady() {
    return databaseClient
        .sql("SELECT 1")
        .map(row -> row.get(0, Integer.class))
        .one()
        .map(result -> result != null && result == 1)
        .onErrorReturn(false);
  }
}
