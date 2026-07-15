package com.autoapi.gateway.retry;

import java.util.UUID;

public record RetryBudgetKey(UUID apiId, String routeId, UUID policyId) {}
