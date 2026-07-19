package com.autoapi.controlplane.rollout;

import com.autoapi.gateway.traffic.StableTrafficHash;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Deterministic rollout cohort ordering derived from rollout and gateway identifiers. */
public final class RolloutCohortRanker {

  private RolloutCohortRanker() {}

  public static long rank(UUID rolloutId, String gatewayId) {
    String material = rolloutId + "|" + gatewayId;
    return StableTrafficHash.nonNegativeBucket(material);
  }

  public static List<RankedGateway> rankGateways(UUID rolloutId, List<String> gatewayIds) {
    List<RankedGateway> ranked = new ArrayList<>(gatewayIds.size());
    for (String gatewayId : gatewayIds) {
      ranked.add(new RankedGateway(gatewayId, rank(rolloutId, gatewayId)));
    }
    ranked.sort(
        Comparator.comparingLong(RankedGateway::cohortRank)
            .thenComparing(RankedGateway::gatewayId));
    return ranked;
  }

  public record RankedGateway(String gatewayId, long cohortRank) {}
}
