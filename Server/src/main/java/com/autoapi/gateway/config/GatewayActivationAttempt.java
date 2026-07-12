package com.autoapi.gateway.config;

public record GatewayActivationAttempt(
    boolean success,
    long version,
    String contentHash,
    long applyDurationMs,
    String errorCode,
    String diagnostic) {

  public static GatewayActivationAttempt success(
      long version, String contentHash, long applyDurationMs) {
    return new GatewayActivationAttempt(true, version, contentHash, applyDurationMs, null, null);
  }

  public static GatewayActivationAttempt failure(
      long version, String contentHash, String errorCode, String diagnostic, long applyDurationMs) {
    return new GatewayActivationAttempt(
        false, version, contentHash, applyDurationMs, errorCode, diagnostic);
  }
}
