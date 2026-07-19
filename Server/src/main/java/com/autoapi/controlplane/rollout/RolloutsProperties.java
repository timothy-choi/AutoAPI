package com.autoapi.controlplane.rollout;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "autoapi.rollouts")
public record RolloutsProperties(
    boolean enabled,
    ProgressionMode defaultProgressionMode,
    Duration defaultStageTimeout,
    Duration defaultObservationDuration,
    int defaultRequiredConvergedPercentage,
    boolean defaultAutoRollback,
    int maxStages,
    int maxActiveRolloutsPerProject,
    Reconciler reconciler,
    GatewayMembership gatewayMembership,
    Labels labels) {

  public RolloutsProperties {
    if (defaultProgressionMode == null) {
      defaultProgressionMode = ProgressionMode.MANUAL;
    }
    if (defaultStageTimeout == null) {
      defaultStageTimeout = Duration.ofMinutes(10);
    }
    if (defaultObservationDuration == null) {
      defaultObservationDuration = Duration.ofMinutes(2);
    }
    if (reconciler == null) {
      reconciler = new Reconciler(Duration.ofSeconds(1), 50);
    }
    if (gatewayMembership == null) {
      gatewayMembership = new GatewayMembership(MembershipMode.SNAPSHOT_AT_START);
    }
    if (labels == null) {
      labels = new Labels(32, 64, 128);
    }
  }

  public enum ProgressionMode {
    MANUAL,
    AUTOMATIC
  }

  public enum MembershipMode {
    SNAPSHOT_AT_START
  }

  public record Reconciler(Duration pollInterval, int batchSize) {
    public Reconciler {
      if (pollInterval == null) {
        pollInterval = Duration.ofSeconds(1);
      }
    }
  }

  public record GatewayMembership(MembershipMode mode) {}

  public record Labels(int maxLabels, int maxKeyLength, int maxValueLength) {}
}
