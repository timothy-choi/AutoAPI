package com.autoapi.controlplane.policy;

/** How contributions for a policy type are combined during inheritance resolution. */
public enum PolicyMergeStrategy {
  OVERRIDE,
  MERGE_MAP,
  APPEND_LIST,
  DISABLE
}
