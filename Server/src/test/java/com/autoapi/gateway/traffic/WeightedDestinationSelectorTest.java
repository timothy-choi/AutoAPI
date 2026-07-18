package com.autoapi.gateway.traffic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.autoapi.config.RuntimeTrafficSplitConfig;
import com.autoapi.config.RuntimeTrafficSplitDestination;
import com.autoapi.config.UpstreamConfig;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WeightedDestinationSelectorTest {

  private static final UUID POLICY_ID = UUID.fromString("00000000-0000-0000-0000-000000000100");
  private static final UUID STABLE_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
  private static final UUID CANARY_ID = UUID.fromString("00000000-0000-0000-0000-000000000102");
  private static final UUID POOL_STABLE = UUID.fromString("00000000-0000-0000-0000-000000000201");
  private static final UUID POOL_CANARY = UUID.fromString("00000000-0000-0000-0000-000000000202");

  @Test
  void sameKeySelectsSameDestination() {
    RuntimeTrafficSplitConfig config = config90_10("fp-stable");
    Optional<RuntimeTrafficSplitDestination> first =
        WeightedDestinationSelector.selectNominalDestination(config, "route-1", "user-0042");
    Optional<RuntimeTrafficSplitDestination> second =
        WeightedDestinationSelector.selectNominalDestination(config, "route-1", "user-0042");
    assertTrue(first.isPresent());
    assertEquals(first.get().destinationId(), second.get().destinationId());
  }

  @Test
  void deterministicDistributionWithinToleranceFor9010() {
    RuntimeTrafficSplitConfig config = config90_10("fp9010");
    int stable = 0;
    int canary = 0;
    for (int i = 1; i <= 10_000; i++) {
      String key = String.format("user-%04d", i);
      RuntimeTrafficSplitDestination destination =
          WeightedDestinationSelector.selectNominalDestination(config, "route-1", key)
              .orElseThrow();
      if (destination.destinationId().equals(STABLE_ID)) {
        stable++;
      } else {
        canary++;
      }
    }
    assertTrue(stable >= 8800 && stable <= 9200, "stable=" + stable);
    assertTrue(canary >= 800 && canary <= 1200, "canary=" + canary);
  }

  @Test
  void zeroWeightDestinationReceivesNoNormalAssignments() {
    RuntimeTrafficSplitConfig config =
        new RuntimeTrafficSplitConfig(
            POLICY_ID,
            "HEADER",
            "X-Test",
            "STRICT",
            "fp-zero",
            100,
            List.of(
                destination(STABLE_ID, "stable", 100, 0, true, POOL_STABLE, 0, 100),
                destination(CANARY_ID, "canary", 0, 1, false, POOL_CANARY, 100, 100)));
    for (int i = 1; i <= 100; i++) {
      RuntimeTrafficSplitDestination selected =
          WeightedDestinationSelector.selectNominalDestination(config, "route-1", "user-" + i)
              .orElseThrow();
      assertEquals(STABLE_ID, selected.destinationId());
    }
  }

  private static RuntimeTrafficSplitConfig config90_10(String fingerprint) {
    return new RuntimeTrafficSplitConfig(
        POLICY_ID,
        "HEADER",
        "X-AutoAPI-Test-User",
        "FALLBACK_TO_PRIMARY",
        fingerprint,
        100,
        List.of(
            destination(STABLE_ID, "stable", 90, 0, true, POOL_STABLE, 0, 90),
            destination(CANARY_ID, "canary", 10, 1, false, POOL_CANARY, 90, 100)));
  }

  private static RuntimeTrafficSplitDestination destination(
      UUID id,
      String name,
      int weight,
      int priority,
      boolean primary,
      UUID poolId,
      int start,
      int end) {
    return new RuntimeTrafficSplitDestination(
        id,
        name,
        weight,
        priority,
        primary,
        UpstreamConfig.single(poolId, UUID.randomUUID(), URI.create("http://" + name + ":8080"), 1),
        start,
        end);
  }
}
