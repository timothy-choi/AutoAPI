package com.autoapi.controlplane.configversion;

import com.autoapi.controlplane.persistence.TrafficSplitDestinationEntity;
import com.autoapi.controlplane.persistence.TrafficSplitPolicyEntity;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class TrafficSplitPolicyFingerprint {

  private TrafficSplitPolicyFingerprint() {}

  public static String compute(
      TrafficSplitPolicyEntity policy, List<TrafficSplitDestinationEntity> destinations) {
    List<TrafficSplitDestinationEntity> sorted =
        destinations.stream()
            .sorted(
                Comparator.comparing(TrafficSplitDestinationEntity::priority)
                    .thenComparing(TrafficSplitDestinationEntity::id))
            .toList();
    StringBuilder builder = new StringBuilder();
    builder
        .append(policy.id())
        .append('|')
        .append(policy.selectionKey().toUpperCase(Locale.ROOT))
        .append('|')
        .append(policy.selectionKeyName() == null ? "" : policy.selectionKeyName())
        .append('|')
        .append(policy.fallbackMode().toUpperCase(Locale.ROOT))
        .append('|');
    for (TrafficSplitDestinationEntity destination : sorted) {
      builder
          .append(destination.id())
          .append(':')
          .append(destination.upstreamPoolId())
          .append(':')
          .append(destination.weight())
          .append(':')
          .append(destination.priority())
          .append(':')
          .append(destination.primary())
          .append(';');
    }
    return RuntimeContentHasher.sha256Hex(builder.toString());
  }
}
