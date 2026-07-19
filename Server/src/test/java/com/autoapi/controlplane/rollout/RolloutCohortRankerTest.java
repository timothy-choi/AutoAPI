package com.autoapi.controlplane.rollout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RolloutCohortRankerTest {

  private static final UUID ROLLOUT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

  @Test
  void rankingIsStableForSameInputs() {
    List<String> gatewayIds = List.of("gateway-a", "gateway-b", "gateway-c");

    List<RolloutCohortRanker.RankedGateway> first =
        RolloutCohortRanker.rankGateways(ROLLOUT_ID, gatewayIds);
    List<RolloutCohortRanker.RankedGateway> second =
        RolloutCohortRanker.rankGateways(ROLLOUT_ID, gatewayIds);

    assertEquals(first, second);
    assertEquals(
        List.of("gateway-a", "gateway-b", "gateway-c"),
        first.stream().map(RolloutCohortRanker.RankedGateway::gatewayId).sorted().toList());
  }

  @Test
  void expansionPreservesEarlierCohortMembers() {
    List<String> gatewayIds = List.of("gateway-a", "gateway-b", "gateway-c");
    List<RolloutCohortRanker.RankedGateway> ranked =
        RolloutCohortRanker.rankGateways(ROLLOUT_ID, gatewayIds);

    List<String> firstStage = RolloutStageCalculator.selectCohort(ranked, 1);
    List<String> secondStage = RolloutStageCalculator.selectCohort(ranked, 2);
    List<String> finalStage = RolloutStageCalculator.selectCohort(ranked, 3);

    assertEquals(1, firstStage.size());
    assertEquals(2, secondStage.size());
    assertEquals(3, finalStage.size());
    assertTrue(secondStage.containsAll(firstStage));
    assertTrue(finalStage.containsAll(secondStage));
  }
}
