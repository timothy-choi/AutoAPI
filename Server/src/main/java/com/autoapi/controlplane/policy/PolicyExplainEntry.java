package com.autoapi.controlplane.policy;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;

/** Explain-mode detail for how a policy type was resolved. */
public record PolicyExplainEntry(
    String policyType,
    PolicyHierarchyLevel winningLevel,
    UUID winningSourceId,
    String winningSourceName,
    int winningRevision,
    PolicyMergeStrategy mergeStrategy,
    JsonNode resolvedValue,
    int contributionCount) {}
