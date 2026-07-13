package com.autoapi.gateway.health;

import com.autoapi.config.UpstreamTargetReference;

/** Result of health-aware upstream selection for one proxy attempt. */
public record SelectedTarget(UpstreamTargetReference target, boolean forcedSelection) {}
