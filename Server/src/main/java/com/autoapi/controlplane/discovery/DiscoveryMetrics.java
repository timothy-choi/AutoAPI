package com.autoapi.controlplane.discovery;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = "autoapi.controlplane.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class DiscoveryMetrics {

  private final Counter registrations;
  private final Counter heartbeats;
  private final Counter heartbeatFailures;
  private final Counter staleTransitions;
  private final Counter recoveries;
  private final Counter deregistrations;
  private final Counter membershipChanges;
  private final Counter snapshotUpdates;

  public DiscoveryMetrics(MeterRegistry meterRegistry) {
    this.registrations =
        Counter.builder("autoapi_service_discovery_registrations_total")
            .description("Service instance registrations")
            .register(meterRegistry);
    this.heartbeats =
        Counter.builder("autoapi_service_discovery_heartbeats_total")
            .description("Successful service instance heartbeats")
            .register(meterRegistry);
    this.heartbeatFailures =
        Counter.builder("autoapi_service_discovery_heartbeat_failures_total")
            .description("Failed service instance heartbeats")
            .register(meterRegistry);
    this.staleTransitions =
        Counter.builder("autoapi_service_discovery_stale_transitions_total")
            .description("Service instance stale transitions")
            .register(meterRegistry);
    this.recoveries =
        Counter.builder("autoapi_service_discovery_recoveries_total")
            .description("Service instance recoveries")
            .register(meterRegistry);
    this.deregistrations =
        Counter.builder("autoapi_service_discovery_deregistrations_total")
            .description("Service instance deregistrations")
            .register(meterRegistry);
    this.membershipChanges =
        Counter.builder("autoapi_service_discovery_membership_changes_total")
            .description("Service discovery membership changes")
            .register(meterRegistry);
    this.snapshotUpdates =
        Counter.builder("autoapi_service_discovery_snapshot_updates_total")
            .description("Runtime snapshots published for discovery membership changes")
            .register(meterRegistry);
  }

  public void recordRegistration(String serviceName, String result) {
    registrations.increment();
    membershipChanges.increment();
  }

  public void recordHeartbeat(String serviceName, String status, String result) {
    heartbeats.increment();
  }

  public void recordHeartbeatFailure(String service, String reason) {
    heartbeatFailures.increment();
  }

  public void recordStaleTransition(String service) {
    staleTransitions.increment();
    membershipChanges.increment();
  }

  public void recordRecovery(String service) {
    recoveries.increment();
    membershipChanges.increment();
  }

  public void recordDeregistration(String service) {
    deregistrations.increment();
    membershipChanges.increment();
  }

  public void recordSnapshotUpdate() {
    snapshotUpdates.increment();
  }
}
