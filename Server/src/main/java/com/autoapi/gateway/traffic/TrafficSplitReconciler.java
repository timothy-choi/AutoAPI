package com.autoapi.gateway.traffic;

import com.autoapi.config.RouteConfig;
import com.autoapi.config.RuntimeConfig;
import com.autoapi.config.RuntimeTrafficSplitConfig;
import com.autoapi.config.UpstreamConfig;
import com.autoapi.gateway.config.ActiveRuntimeBundle;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Reconciles gateway-local traffic-split counters when runtime configuration activates. */
public final class TrafficSplitReconciler {

  private TrafficSplitReconciler() {}

  public static void reconcile(TrafficSplitRegistry registry, ActiveRuntimeBundle bundle) {
    RuntimeConfig config = bundle.runtimeConfig();
    Set<UUID> activePolicyIds = new HashSet<>();
    Map<UUID, String> fingerprintByPolicy = new HashMap<>();
    for (RouteConfig route : config.routes()) {
      RuntimeTrafficSplitConfig split = route.trafficSplit();
      if (split == null) {
        continue;
      }
      activePolicyIds.add(split.policyId());
      fingerprintByPolicy.putIfAbsent(split.policyId(), split.fingerprint());
    }
    registry.reconcile(activePolicyIds, fingerprintByPolicy);
  }

  public static int countTargets(ActiveRuntimeBundle bundle) {
    int total = 0;
    for (RouteConfig route : bundle.runtimeConfig().routes()) {
      if (route.trafficSplitEnabled()) {
        for (var destination : route.trafficSplit().destinations()) {
          total += Math.max(1, destination.upstreamPool().targets().size());
        }
      } else if (route.upstream() != null) {
        UpstreamConfig upstream = route.upstream();
        total += upstream.targets().isEmpty() ? 1 : upstream.targets().size();
      }
    }
    return total;
  }
}
