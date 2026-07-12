package com.autoapi.controlplane.configversion;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"consecutiveFailureThreshold", "ejectionDurationSeconds", "maxEjectionPercent"})
public record CompiledBackendHealthSection(
    int consecutiveFailureThreshold, int ejectionDurationSeconds, int maxEjectionPercent) {}
