package com.autoapi.gateway.health;

import java.util.UUID;

/** Stable operational-health identity for an upstream target within a pool. */
public record TargetKey(UUID apiId, UUID poolId, UUID targetId) {}
