package com.autoapi.controlplane.rollout;

import java.util.UUID;

/** Effective desired configuration version for a gateway and API. */
public record EffectiveDesiredConfig(
    UUID apiId,
    long version,
    String contentHash,
    String snapshotUrl,
    UUID rolloutId,
    Integer rolloutStageIndex,
    long assignmentGeneration,
    EffectiveDesiredSource source) {

  public enum EffectiveDesiredSource {
    API_DEFAULT,
    GROUP_DESIRED,
    ROLLOUT_ASSIGNMENT,
    ROLLBACK_ASSIGNMENT
  }
}
