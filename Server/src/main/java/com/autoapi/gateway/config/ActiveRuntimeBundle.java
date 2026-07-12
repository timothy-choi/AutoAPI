package com.autoapi.gateway.config;

import com.autoapi.config.RuntimeConfig;
import java.util.UUID;

public final class ActiveRuntimeBundle {

  private final UUID apiId;
  private final long version;
  private final String contentHash;
  private final RuntimeConfig runtimeConfig;

  public ActiveRuntimeBundle(
      UUID apiId, long version, String contentHash, RuntimeConfig runtimeConfig) {
    this.apiId = apiId;
    this.version = version;
    this.contentHash = contentHash;
    this.runtimeConfig = runtimeConfig;
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
}
