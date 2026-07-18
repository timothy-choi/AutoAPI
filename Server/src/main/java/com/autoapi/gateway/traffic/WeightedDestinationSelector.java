package com.autoapi.gateway.traffic;

import com.autoapi.config.RuntimeTrafficSplitConfig;
import com.autoapi.config.RuntimeTrafficSplitDestination;
import java.util.Optional;

public final class WeightedDestinationSelector {

  private WeightedDestinationSelector() {}

  public static Optional<RuntimeTrafficSplitDestination> selectNominalDestination(
      RuntimeTrafficSplitConfig config, String routeId, String selectionKeyValue) {
    if (config.totalWeight() <= 0) {
      return Optional.empty();
    }
    String material =
        StableTrafficHash.hashMaterial(
            routeId, config.policyId().toString(), config.fingerprint(), selectionKeyValue);
    long bucket = StableTrafficHash.nonNegativeBucket(material);
    int selectedBucket = StableTrafficHash.bucketModTotal(bucket, config.totalWeight());
    for (RuntimeTrafficSplitDestination destination : config.destinations()) {
      if (destination.weight() <= 0) {
        continue;
      }
      if (selectedBucket >= destination.cumulativeWeightStart()
          && selectedBucket < destination.cumulativeWeightEnd()) {
        return Optional.of(destination);
      }
    }
    return Optional.empty();
  }
}
