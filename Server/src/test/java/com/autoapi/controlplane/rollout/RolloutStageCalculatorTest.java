package com.autoapi.controlplane.rollout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.autoapi.controlplane.api.ControlPlaneException;
import com.autoapi.controlplane.rollout.RolloutStageCalculator.StageDefinitionInput;
import java.util.List;
import org.junit.jupiter.api.Test;

class RolloutStageCalculatorTest {

  @Test
  void zeroEligibleGatewaysReturnsZeroCount() {
    assertEquals(0, RolloutStageCalculator.stageGatewayCount(0, 50, 1, null));
    assertEquals(List.of(), RolloutStageCalculator.selectCohort(List.of(), 3));
  }

  @Test
  void stageCountsAreIntegerSafe() {
    assertEquals(1, RolloutStageCalculator.stageGatewayCount(3, 33, 1, null));
    assertEquals(2, RolloutStageCalculator.stageGatewayCount(3, 50, 1, null));
    assertEquals(3, RolloutStageCalculator.stageGatewayCount(3, 100, 1, null));
    assertEquals(2, RolloutStageCalculator.stageGatewayCount(3, 34, 1, 2));
  }

  @Test
  void validateStageDefinitionsRequiresMonotonicPercentages() {
    List<StageDefinitionInput> stages = List.of(stage(33), stage(33), stage(100));

    assertThrows(
        ControlPlaneException.class,
        () -> RolloutStageCalculator.validateStageDefinitions(stages, 10));
  }

  @Test
  void validateStageDefinitionsRequiresFinalHundredPercentStage() {
    List<StageDefinitionInput> stages = List.of(stage(50));

    assertThrows(
        ControlPlaneException.class,
        () -> RolloutStageCalculator.validateStageDefinitions(stages, 10));
  }

  private static StageDefinitionInput stage(int percentage) {
    return new StageDefinitionInput(percentage, 1, null, 100, 0, 0, 0, 1_000L, 60_000L);
  }
}
