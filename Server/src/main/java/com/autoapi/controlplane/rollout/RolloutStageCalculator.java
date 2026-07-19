package com.autoapi.controlplane.rollout;

import com.autoapi.controlplane.api.ControlPlaneException;
import java.util.ArrayList;
import java.util.List;

/** Integer-safe stage size and threshold calculations for progressive rollouts. */
public final class RolloutStageCalculator {

  private RolloutStageCalculator() {}

  public static int stageGatewayCount(
      int totalEligibleGateways,
      int percentage,
      int minimumGatewayCount,
      Integer maximumGatewayCount) {
    if (totalEligibleGateways <= 0) {
      return 0;
    }
    if (percentage >= 100) {
      return totalEligibleGateways;
    }
    int selected = (int) Math.ceil(totalEligibleGateways * (percentage / 100.0));
    selected = Math.max(selected, minimumGatewayCount);
    if (maximumGatewayCount != null) {
      selected = Math.min(selected, maximumGatewayCount);
    }
    selected = Math.min(selected, totalEligibleGateways);
    return Math.max(1, selected);
  }

  public static List<String> selectCohort(
      List<RolloutCohortRanker.RankedGateway> rankedGateways, int count) {
    if (count <= 0 || rankedGateways.isEmpty()) {
      return List.of();
    }
    int limit = Math.min(count, rankedGateways.size());
    List<String> selected = new ArrayList<>(limit);
    for (int i = 0; i < limit; i++) {
      selected.add(rankedGateways.get(i).gatewayId());
    }
    return List.copyOf(selected);
  }

  public static void validateStageDefinitions(List<StageDefinitionInput> stages, int maxStages) {
    if (stages == null || stages.isEmpty()) {
      throw ControlPlaneException.invalidRequest("At least one rollout stage is required");
    }
    if (stages.size() > maxStages) {
      throw ControlPlaneException.invalidRequest("Too many rollout stages");
    }
    int previousPercentage = 0;
    boolean hasFinalStage = false;
    for (int i = 0; i < stages.size(); i++) {
      StageDefinitionInput stage = stages.get(i);
      if (stage.percentage() <= 0 || stage.percentage() > 100) {
        throw ControlPlaneException.invalidRequest("Stage percentage must be between 1 and 100");
      }
      if (stage.percentage() <= previousPercentage && i > 0) {
        throw ControlPlaneException.invalidRequest("Stage percentages must increase monotonically");
      }
      if (stage.requiredConvergedPercentage() <= 0 || stage.requiredConvergedPercentage() > 100) {
        throw ControlPlaneException.invalidRequest("requiredConvergedPercentage is invalid");
      }
      if (stage.minimumGatewayCount() < 1) {
        throw ControlPlaneException.invalidRequest("minimumGatewayCount must be at least 1");
      }
      if (stage.maximumFailedGateways() < 0 || stage.maximumTimedOutGateways() < 0) {
        throw ControlPlaneException.invalidRequest("failure thresholds must be nonnegative");
      }
      if (stage.stageTimeoutMs() <= 0 || stage.observationDurationMs() <= 0) {
        throw ControlPlaneException.invalidRequest("timeouts must be positive");
      }
      if (stage.percentage() == 100) {
        hasFinalStage = true;
      }
      previousPercentage = stage.percentage();
    }
    if (!hasFinalStage) {
      throw ControlPlaneException.invalidRequest("Final stage must reach 100%");
    }
  }

  public record StageDefinitionInput(
      int percentage,
      int minimumGatewayCount,
      Integer maximumGatewayCount,
      int requiredConvergedPercentage,
      int maximumFailedGateways,
      int maximumTimedOutGateways,
      int requiredOnlinePercentage,
      long observationDurationMs,
      long stageTimeoutMs) {}
}
