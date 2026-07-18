package com.autoapi.gateway.config;

import com.autoapi.controlplane.configversion.StoredRuntimeSnapshot;
import com.autoapi.gateway.GatewayProperties;
import com.autoapi.gateway.config.remote.RemoteSnapshotAdapter;
import com.autoapi.gateway.config.remote.RemoteSnapshotValidationException;
import com.autoapi.gateway.health.GatewayHealthReconciler;
import com.autoapi.gateway.health.TargetHealthRegistry;
import com.autoapi.gateway.observability.GatewayInstanceIdentity;
import com.autoapi.gateway.observability.GatewayObservabilityMetrics;
import com.autoapi.gateway.observability.GatewayStructuredLogger;
import com.autoapi.gateway.retry.RetryBudgetRegistry;
import com.autoapi.gateway.traffic.TrafficSplitReconciler;
import com.autoapi.gateway.traffic.TrafficSplitRegistry;
import com.autoapi.runtime.AutoApiRole;
import com.autoapi.runtime.ConditionalOnAutoApiRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnAutoApiRole({AutoApiRole.GATEWAY, AutoApiRole.COMBINED})
public class LocalGatewayConfigActivator {

  private static final Logger log = LoggerFactory.getLogger(LocalGatewayConfigActivator.class);

  private final ActiveRuntimeConfigHolder activeRuntimeConfigHolder;
  private final GatewayProperties gatewayProperties;
  private final ObjectProvider<TargetHealthRegistry> targetHealthRegistry;
  private final ObjectProvider<RetryBudgetRegistry> retryBudgetRegistry;
  private final ObjectProvider<TrafficSplitRegistry> trafficSplitRegistry;
  private final ObjectProvider<GatewayStructuredLogger> structuredLoggerProvider;
  private final ObjectProvider<GatewayObservabilityMetrics> observabilityMetricsProvider;
  private final ObjectProvider<GatewayInstanceIdentity> instanceIdentityProvider;

  public LocalGatewayConfigActivator(
      ActiveRuntimeConfigHolder activeRuntimeConfigHolder,
      GatewayProperties gatewayProperties,
      ObjectProvider<TargetHealthRegistry> targetHealthRegistry,
      ObjectProvider<RetryBudgetRegistry> retryBudgetRegistry,
      ObjectProvider<TrafficSplitRegistry> trafficSplitRegistry,
      ObjectProvider<GatewayStructuredLogger> structuredLoggerProvider,
      ObjectProvider<GatewayObservabilityMetrics> observabilityMetricsProvider,
      ObjectProvider<GatewayInstanceIdentity> instanceIdentityProvider) {
    this.activeRuntimeConfigHolder = activeRuntimeConfigHolder;
    this.gatewayProperties = gatewayProperties;
    this.targetHealthRegistry = targetHealthRegistry;
    this.retryBudgetRegistry = retryBudgetRegistry;
    this.trafficSplitRegistry = trafficSplitRegistry;
    this.structuredLoggerProvider = structuredLoggerProvider;
    this.observabilityMetricsProvider = observabilityMetricsProvider;
    this.instanceIdentityProvider = instanceIdentityProvider;
  }

  public GatewayActivationAttempt activateCandidate(StoredRuntimeSnapshot snapshot) {
    long started = System.nanoTime();
    try {
      ActiveRuntimeBundle candidate =
          RemoteSnapshotAdapter.toActiveBundle(snapshot, gatewayProperties.apiId());
      ActiveRuntimeBundle previous = activeRuntimeConfigHolder.getActive();
      targetHealthRegistry.ifAvailable(
          registry -> GatewayHealthReconciler.reconcile(registry, candidate));
      retryBudgetRegistry.ifAvailable(registry -> registry.reconcile(candidate));
      trafficSplitRegistry.ifAvailable(
          registry -> TrafficSplitReconciler.reconcile(registry, candidate));
      activeRuntimeConfigHolder.activate(candidate);
      long durationMs = (System.nanoTime() - started) / 1_000_000L;
      int targetCount = TrafficSplitReconciler.countTargets(candidate);
      GatewayStructuredLogger structuredLogger = structuredLoggerProvider.getIfAvailable();
      if (structuredLogger != null) {
        GatewayInstanceIdentity identity = instanceIdentityProvider.getIfAvailable();
        structuredLogger.runtimeSnapshotActivated(
            identity == null ? "unknown" : identity.instanceId(),
            previous == null ? "" : String.valueOf(previous.version()),
            String.valueOf(candidate.version()),
            candidate.version(),
            candidate.runtimeConfig().routes().size(),
            targetCount,
            durationMs);
      }
      GatewayObservabilityMetrics metrics = observabilityMetricsProvider.getIfAvailable();
      if (metrics != null) {
        metrics.recordRuntimeSnapshotInfo(
            String.valueOf(candidate.version()),
            candidate.runtimeConfig().routes().size(),
            targetCount,
            candidate.version());
      }
      log.info(
          "Activated gateway configuration apiId={} version={} contentHashPrefix={} routes={}"
              + " targets={} durationMs={}",
          candidate.apiId(),
          candidate.version(),
          hashPrefix(candidate.contentHash()),
          candidate.runtimeConfig().routes().size(),
          TrafficSplitReconciler.countTargets(candidate),
          durationMs);
      return GatewayActivationAttempt.success(
          candidate.version(), candidate.contentHash(), durationMs);
    } catch (RemoteSnapshotValidationException ex) {
      long durationMs = (System.nanoTime() - started) / 1_000_000L;
      log.warn(
          "Rejected gateway configuration candidate apiId={} reason={}",
          gatewayProperties.apiId(),
          ex.getMessage());
      return GatewayActivationAttempt.failure(
          snapshot.version(),
          snapshot.contentHash(),
          mapErrorCode(ex),
          safeDiagnostic(ex.getMessage()),
          durationMs);
    }
  }

  private static String mapErrorCode(RemoteSnapshotValidationException ex) {
    String message = ex.getMessage() == null ? "" : ex.getMessage();
    if (message.contains("hash")) {
      return "CONTENT_HASH_MISMATCH";
    }
    if (message.contains("ROUND_ROBIN")) {
      return "UNSUPPORTED_RUNTIME_FEATURE";
    }
    return "SNAPSHOT_VALIDATION_FAILED";
  }

  private static String safeDiagnostic(String message) {
    if (message == null) {
      return "Validation failed";
    }
    return message.length() > 1024 ? message.substring(0, 1024) : message;
  }

  private static String hashPrefix(String hash) {
    if (hash == null || hash.length() < 12) {
      return hash;
    }
    return hash.substring(0, 12);
  }
}
