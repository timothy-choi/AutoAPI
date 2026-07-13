package com.autoapi.gateway.health;

import com.autoapi.config.RouteConfig;
import com.autoapi.config.RuntimeConfig;
import com.autoapi.config.UpstreamConfig;
import com.autoapi.config.UpstreamTargetReference;
import com.autoapi.gateway.config.ActiveRuntimeBundle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Reconciles gateway-local passive health state when a new runtime configuration activates. */
public final class GatewayHealthReconciler {

  private GatewayHealthReconciler() {}

  public static void reconcile(TargetHealthRegistry registry, ActiveRuntimeBundle bundle) {
    RuntimeConfig config = bundle.runtimeConfig();
    Map<UUID, List<TargetFingerprint>> targetsByPool = new HashMap<>();
    for (RouteConfig route : config.routes()) {
      UpstreamConfig upstream = route.upstream();
      if (upstream.poolId() == null || upstream.targets().isEmpty()) {
        continue;
      }
      List<TargetFingerprint> fingerprints = new ArrayList<>();
      for (UpstreamTargetReference target : upstream.targets()) {
        fingerprints.add(TargetFingerprint.of(target.targetId(), target.url()));
      }
      targetsByPool.merge(
          upstream.poolId(),
          fingerprints,
          (existing, added) -> {
            List<TargetFingerprint> merged = new ArrayList<>(existing);
            for (TargetFingerprint fingerprint : added) {
              if (!merged.contains(fingerprint)) {
                merged.add(fingerprint);
              }
            }
            return merged;
          });
    }
    registry.reconcile(bundle.apiId(), targetsByPool);
  }
}
