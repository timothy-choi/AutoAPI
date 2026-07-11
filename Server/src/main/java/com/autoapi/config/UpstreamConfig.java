package com.autoapi.config;

import java.net.URI;
import java.util.List;

public record UpstreamConfig(URI url, List<URI> roundRobinTargets) {

  public UpstreamConfig {
    roundRobinTargets =
        roundRobinTargets == null || roundRobinTargets.isEmpty()
            ? List.of()
            : List.copyOf(roundRobinTargets);
  }

  public UpstreamConfig(URI url) {
    this(url, List.of());
  }

  public static UpstreamConfig single(URI url) {
    return new UpstreamConfig(url, List.of());
  }

  public static UpstreamConfig roundRobin(List<URI> targets) {
    if (targets == null || targets.isEmpty()) {
      throw new IllegalArgumentException("Round robin targets must not be empty");
    }
    return new UpstreamConfig(targets.getFirst(), List.copyOf(targets));
  }
}
