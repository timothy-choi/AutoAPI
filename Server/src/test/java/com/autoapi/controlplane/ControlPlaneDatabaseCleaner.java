package com.autoapi.controlplane;

import org.springframework.r2dbc.core.DatabaseClient;

/**
 * Clears control-plane tables in foreign-key-safe order for shared PostgreSQL integration tests.
 */
public final class ControlPlaneDatabaseCleaner {

  private ControlPlaneDatabaseCleaner() {}

  public static void cleanAll(DatabaseClient databaseClient) {
    databaseClient.sql("DELETE FROM webhook_delivery_attempts").fetch().rowsUpdated().block();
    databaseClient.sql("DELETE FROM webhook_deliveries").fetch().rowsUpdated().block();
    databaseClient.sql("DELETE FROM webhook_subscriptions").fetch().rowsUpdated().block();
    databaseClient.sql("DELETE FROM platform_events").fetch().rowsUpdated().block();
    databaseClient.sql("DELETE FROM service_instances").fetch().rowsUpdated().block();
    databaseClient
        .sql("DELETE FROM service_registration_credentials")
        .fetch()
        .rowsUpdated()
        .block();
    databaseClient.sql("DELETE FROM discovered_services").fetch().rowsUpdated().block();
    databaseClient.sql("DELETE FROM request_summaries").fetch().rowsUpdated().block();
    databaseClient.sql("DELETE FROM gateway_instances").fetch().rowsUpdated().block();
    databaseClient.sql("DELETE FROM config_activation_events").fetch().rowsUpdated().block();
    databaseClient.sql("DELETE FROM gateway_api_status").fetch().rowsUpdated().block();
    databaseClient.sql("DELETE FROM runtime_rollout_gateways").fetch().rowsUpdated().block();
    databaseClient.sql("DELETE FROM runtime_rollout_stages").fetch().rowsUpdated().block();
    databaseClient.sql("DELETE FROM runtime_rollouts").fetch().rowsUpdated().block();
    databaseClient.sql("DELETE FROM gateway_group_memberships").fetch().rowsUpdated().block();
    databaseClient.sql("DELETE FROM gateway_groups").fetch().rowsUpdated().block();
    databaseClient.sql("DELETE FROM gateways").fetch().rowsUpdated().block();
    databaseClient.sql("DELETE FROM config_versions").fetch().rowsUpdated().block();
    databaseClient.sql("DELETE FROM route_policy_bindings").fetch().rowsUpdated().block();
    databaseClient.sql("DELETE FROM traffic_split_destinations").fetch().rowsUpdated().block();
    databaseClient.sql("DELETE FROM traffic_split_policies").fetch().rowsUpdated().block();
    databaseClient.sql("DELETE FROM retry_policies").fetch().rowsUpdated().block();
    databaseClient.sql("DELETE FROM circuit_breaker_policies").fetch().rowsUpdated().block();
    databaseClient.sql("DELETE FROM api_keys").fetch().rowsUpdated().block();
    databaseClient.sql("DELETE FROM rate_limit_policies").fetch().rowsUpdated().block();
    databaseClient.sql("DELETE FROM routes").fetch().rowsUpdated().block();
    databaseClient.sql("DELETE FROM upstream_targets").fetch().rowsUpdated().block();
    databaseClient.sql("DELETE FROM upstream_pools").fetch().rowsUpdated().block();
    databaseClient.sql("DELETE FROM backend_health_policies").fetch().rowsUpdated().block();
    databaseClient.sql("DELETE FROM apis").fetch().rowsUpdated().block();
    databaseClient.sql("DELETE FROM projects").fetch().rowsUpdated().block();
  }
}
