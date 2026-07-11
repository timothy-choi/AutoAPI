package com.autoapi.gateway;

import java.time.Duration;

public class ControlPlaneClientProperties {

  private Duration connectTimeout = Duration.ofSeconds(5);
  private Duration responseTimeout = Duration.ofSeconds(15);
  private int maxInMemorySizeBytes = 4 * 1024 * 1024;

  public Duration connectTimeout() {
    return connectTimeout;
  }

  public void setConnectTimeout(Duration connectTimeout) {
    this.connectTimeout = connectTimeout;
  }

  public Duration responseTimeout() {
    return responseTimeout;
  }

  public void setResponseTimeout(Duration responseTimeout) {
    this.responseTimeout = responseTimeout;
  }

  public int maxInMemorySizeBytes() {
    return maxInMemorySizeBytes;
  }

  public void setMaxInMemorySizeBytes(int maxInMemorySizeBytes) {
    this.maxInMemorySizeBytes = maxInMemorySizeBytes;
  }
}
