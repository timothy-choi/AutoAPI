package com.autoapi.gateway;

public class GatewayRetryProperties {

  private long maxReplayBodyBytes = 1048576;

  public long maxReplayBodyBytes() {
    return maxReplayBodyBytes;
  }

  public void setMaxReplayBodyBytes(long maxReplayBodyBytes) {
    this.maxReplayBodyBytes = maxReplayBodyBytes;
  }
}
