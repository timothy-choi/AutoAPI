package com.autoapi.controlplane.policy;

import java.util.UUID;

/** Dry-run policy evaluation request. */
public record PolicyEvaluationRequest(UUID apiId, UUID routeId, boolean explain) {}
