package com.autoapi.controlplane.policy;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;

/** A single policy value contributed from a hierarchy level. */
public record PolicyContribution(
    String policyType,
    JsonNode value,
    PolicyHierarchyLevel sourceLevel,
    UUID sourceId,
    String sourceName,
    int bundleRevision,
    boolean override) {}
