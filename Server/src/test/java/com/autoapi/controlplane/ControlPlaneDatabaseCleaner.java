package com.autoapi.controlplane;

import org.springframework.r2dbc.core.DatabaseClient;

/**
 * Clears control-plane tables in foreign-key-safe order for shared PostgreSQL integration tests.
 */
public final class ControlPlaneDatabaseCleaner {

  private ControlPlaneDatabaseCleaner() {}

  public static void cleanAll(DatabaseClient databaseClient) {
    databaseClient.sql("DELETE FROM config_activation_events").fetch().rowsUpdated().block();
    databaseClient.sql("DELETE FROM gateway_api_status").fetch().rowsUpdated().block();
    databaseClient.sql("DELETE FROM gateways").fetch().rowsUpdated().block();
    databaseClient.sql("DELETE FROM config_versions").fetch().rowsUpdated().block();
    databaseClient.sql("DELETE FROM route_policy_bindings").fetch().rowsUpdated().block();
    databaseClient.sql("DELETE FROM api_keys").fetch().rowsUpdated().block();
    databaseClient.sql("DELETE FROM rate_limit_policies").fetch().rowsUpdated().block();
    databaseClient.sql("DELETE FROM routes").fetch().rowsUpdated().block();
    databaseClient.sql("DELETE FROM upstream_targets").fetch().rowsUpdated().block();
    databaseClient.sql("DELETE FROM upstream_pools").fetch().rowsUpdated().block();
    databaseClient.sql("DELETE FROM apis").fetch().rowsUpdated().block();
    databaseClient.sql("DELETE FROM projects").fetch().rowsUpdated().block();
  }
}
