package com.autoapi.gateway.config;

import com.autoapi.gateway.GatewayProperties;
import com.autoapi.gateway.config.remote.RemoteSnapshotAdapter;
import com.autoapi.gateway.config.remote.RemoteSnapshotValidationException;
import com.autoapi.runtime.AutoApiRole;
import com.autoapi.runtime.ConditionalOnAutoApiRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnAutoApiRole({AutoApiRole.GATEWAY, AutoApiRole.COMBINED})
public class LocalGatewayConfigActivator {

  private static final Logger log = LoggerFactory.getLogger(LocalGatewayConfigActivator.class);

  private final ActiveRuntimeConfigHolder activeRuntimeConfigHolder;
  private final GatewayProperties gatewayProperties;

  public LocalGatewayConfigActivator(
      ActiveRuntimeConfigHolder activeRuntimeConfigHolder, GatewayProperties gatewayProperties) {
    this.activeRuntimeConfigHolder = activeRuntimeConfigHolder;
    this.gatewayProperties = gatewayProperties;
  }

  public boolean activateCandidate(
      com.autoapi.controlplane.configversion.StoredRuntimeSnapshot snapshot) {
    try {
      ActiveRuntimeBundle candidate =
          RemoteSnapshotAdapter.toActiveBundle(snapshot, gatewayProperties.apiId());
      long started = System.nanoTime();
      activeRuntimeConfigHolder.activate(candidate);
      long durationMs = (System.nanoTime() - started) / 1_000_000L;
      log.info(
          "Activated gateway configuration apiId={} version={} contentHashPrefix={} routes={}"
              + " targets={} durationMs={}",
          candidate.apiId(),
          candidate.version(),
          hashPrefix(candidate.contentHash()),
          candidate.runtimeConfig().routes().size(),
          countTargets(candidate),
          durationMs);
      return true;
    } catch (RemoteSnapshotValidationException ex) {
      log.warn(
          "Rejected gateway configuration candidate apiId={} reason={}",
          gatewayProperties.apiId(),
          ex.getMessage());
      return false;
    }
  }

  private static int countTargets(ActiveRuntimeBundle bundle) {
    return bundle.runtimeConfig().routes().stream()
        .mapToInt(route -> Math.max(1, route.upstream().roundRobinTargets().size()))
        .sum();
  }

  private static String hashPrefix(String hash) {
    if (hash == null || hash.length() < 12) {
      return hash;
    }
    return hash.substring(0, 12);
  }
}
