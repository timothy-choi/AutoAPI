package com.autoapi.gateway.config.remote;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Tracks the latest rollout assignment metadata from control-plane desired-config polling. */
@Component
@ConditionalOnProperty(name = "autoapi.gateway.config-source", havingValue = "control-plane")
public class RolloutAssignmentContextHolder {

  private final AtomicReference<RolloutAssignmentContext> current = new AtomicReference<>();

  public void update(UUID rolloutId, Integer rolloutStageIndex, Long assignmentGeneration) {
    if (rolloutId == null || assignmentGeneration == null) {
      current.set(null);
      return;
    }
    current.set(new RolloutAssignmentContext(rolloutId, rolloutStageIndex, assignmentGeneration));
  }

  public RolloutAssignmentContext get() {
    return current.get();
  }

  public record RolloutAssignmentContext(
      UUID rolloutId, Integer rolloutStageIndex, long assignmentGeneration) {}
}
