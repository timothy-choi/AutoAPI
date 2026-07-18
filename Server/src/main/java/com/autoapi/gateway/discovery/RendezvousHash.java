package com.autoapi.gateway.discovery;

import com.autoapi.config.RuntimeDiscoveredInstance;
import com.autoapi.config.RuntimeDiscoveredServiceConfig;
import com.autoapi.config.UpstreamTargetReference;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;

public final class RendezvousHash {

  private RendezvousHash() {}

  public static RuntimeDiscoveredInstance select(
      RuntimeDiscoveredServiceConfig service,
      String hashMaterial,
      List<RuntimeDiscoveredInstance> instances) {
    if (instances == null || instances.isEmpty()) {
      throw new IllegalArgumentException("Discovered service has no eligible instances");
    }
    RuntimeDiscoveredInstance best = null;
    long bestScore = Long.MIN_VALUE;
    for (RuntimeDiscoveredInstance instance : instances) {
      long score = score(hashMaterial, instance);
      if (best == null || score > bestScore) {
        best = instance;
        bestScore = score;
      } else if (score == bestScore && best != null) {
        if (instance.instanceId().compareTo(best.instanceId()) < 0) {
          best = instance;
        }
      }
    }
    return best;
  }

  public static List<UpstreamTargetReference> toTargetReferences(
      List<RuntimeDiscoveredInstance> instances) {
    return instances.stream()
        .sorted(Comparator.comparing(RuntimeDiscoveredInstance::instanceId))
        .map(
            instance ->
                new UpstreamTargetReference(instance.targetId(), instance.url(), instance.weight()))
        .toList();
  }

  private static long score(String hashMaterial, RuntimeDiscoveredInstance instance) {
    String material =
        hashMaterial + ":" + instance.instanceId() + ":" + instance.registrationEpoch();
    return StableHash.fnv1a64(material);
  }

  static final class StableHash {
    private StableHash() {}

    static long fnv1a64(String value) {
      byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
      long hash = 0xcbf29ce484222325L;
      for (byte b : bytes) {
        hash ^= b & 0xff;
        hash *= 0x100000001b3L;
      }
      return hash;
    }
  }
}
