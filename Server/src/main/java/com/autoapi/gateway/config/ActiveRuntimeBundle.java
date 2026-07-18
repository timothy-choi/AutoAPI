package com.autoapi.gateway.config;

import com.autoapi.config.RuntimeConfig;
import com.autoapi.config.RuntimeSnapshotMetadata;
import java.time.Instant;
import java.util.UUID;

public final class ActiveRuntimeBundle {

  private final UUID apiId;
  private final long version;
  private final String contentHash;
  private final RuntimeConfig runtimeConfig;
  private final RuntimeSnapshotMetadata metadata;
  private final Instant activatedAt;

  public ActiveRuntimeBundle(
      UUID apiId,
      long version,
      String contentHash,
      RuntimeConfig runtimeConfig,
      RuntimeSnapshotMetadata metadata,
      Instant activatedAt) {
    this.apiId = apiId;
    this.version = version;
    this.contentHash = contentHash;
    this.runtimeConfig = runtimeConfig;
    this.metadata = metadata;
    this.activatedAt = activatedAt;
  }

  public ActiveRuntimeBundle(
      UUID apiId, long version, String contentHash, RuntimeConfig runtimeConfig) {
    this(apiId, version, contentHash, runtimeConfig, null, Instant.now());
  }

  public UUID apiId() {
    return apiId;
  }

  public long version() {
    return version;
  }

  public String contentHash() {
    return contentHash;
  }

  public RuntimeConfig runtimeConfig() {
    return runtimeConfig;
  }

  public RuntimeSnapshotMetadata metadata() {
    return metadata;
  }

  public Instant activatedAt() {
    return activatedAt;
  }
}
