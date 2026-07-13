package com.autoapi.gateway;

public class GatewayRetryProperties {

  private long maxReplayBodyBytes = 1048576;
  private long upstreamConnectTimeoutMs = 1000;

  public long maxReplayBodyBytes() {
    return maxReplayBodyBytes;
  }

  public void setMaxReplayBodyBytes(long maxReplayBodyBytes) {
    this.maxReplayBodyBytes = maxReplayBodyBytes;
  }

  public long upstreamConnectTimeoutMs() {
    return upstreamConnectTimeoutMs;
  }

  public void setUpstreamConnectTimeoutMs(long upstreamConnectTimeoutMs) {
    this.upstreamConnectTimeoutMs = upstreamConnectTimeoutMs;
  }
}
