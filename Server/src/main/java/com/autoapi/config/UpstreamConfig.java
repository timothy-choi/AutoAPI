package com.autoapi.config;

import java.net.URI;
import java.util.List;
import java.util.UUID;

public record UpstreamConfig(
    UUID poolId,
    URI url,
    List<UpstreamTargetReference> targets,
    BackendHealthPolicyConfig backendHealth) {

  public UpstreamConfig {
    targets = targets == null || targets.isEmpty() ? List.of() : List.copyOf(targets);
  }

  public UpstreamConfig(URI url) {
    this(null, url, List.of(), null);
  }

  public static UpstreamConfig single(UUID poolId, UUID targetId, URI url, int weight) {
    return new UpstreamConfig(
        poolId, url, List.of(new UpstreamTargetReference(targetId, url, weight)), null);
  }

  public static UpstreamConfig roundRobin(
      UUID poolId, List<UpstreamTargetReference> targets, BackendHealthPolicyConfig health) {
    if (targets == null || targets.isEmpty()) {
      throw new IllegalArgumentException("Round robin targets must not be empty");
    }
    return new UpstreamConfig(poolId, targets.getFirst().url(), List.copyOf(targets), health);
  }

  /** Legacy helper for tests using URI-only targets. */
  public static UpstreamConfig single(URI url) {
    return new UpstreamConfig(null, url, List.of(), null);
  }

  /** Legacy helper for tests using URI-only round robin. */
  public static UpstreamConfig roundRobin(List<URI> targetUris) {
    if (targetUris == null || targetUris.isEmpty()) {
      throw new IllegalArgumentException("Round robin targets must not be empty");
    }
    List<UpstreamTargetReference> refs =
        targetUris.stream()
            .map(uri -> new UpstreamTargetReference(UUID.randomUUID(), uri, 1))
            .toList();
    return new UpstreamConfig(null, targetUris.getFirst(), refs, null);
  }

  public List<URI> roundRobinTargets() {
    return targets.stream().map(UpstreamTargetReference::url).toList();
  }

  public boolean hasHealthPolicy() {
    return backendHealth != null;
  }
}
