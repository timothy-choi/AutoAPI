package com.autoapi.controlplane.policy;

/** Registered policy type with its merge semantics. */
public record PolicyTypeDefinition(String typeName, PolicyMergeStrategy mergeStrategy) {}
