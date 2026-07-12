package com.autoapi.config;

public record BackendHealthPolicyConfig(
    int consecutiveFailureThreshold, int ejectionDurationSeconds, int maxEjectionPercent) {}
