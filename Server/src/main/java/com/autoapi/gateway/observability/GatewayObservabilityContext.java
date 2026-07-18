package com.autoapi.gateway.observability;

import com.autoapi.gateway.circuitbreaker.CircuitBreakerState;

/** Mutable per-request observability state stored on the exchange. */
public final class GatewayObservabilityContext {

  private String apiId;
  private String routeId;
  private String normalizedPath;
  private String selectedPool;
  private String selectedTargetId;
  private int attemptCount;
  private int retryCount;
  private boolean fallbackUsed;
  private CircuitBreakerState circuitBreakerState;
  private String healthState;
  private GatewayErrorType errorType = GatewayErrorType.NONE;
  private long bytesReceived;
  private long bytesSent;
  private boolean clientDisconnected;
  private String snapshotVersion;
  private String traceId;
  private String spanId;

  public String apiId() {
    return apiId;
  }

  public void setApiId(String apiId) {
    this.apiId = apiId;
  }

  public String routeId() {
    return routeId;
  }

  public void setRouteId(String routeId) {
    this.routeId = routeId;
  }

  public String normalizedPath() {
    return normalizedPath;
  }

  public void setNormalizedPath(String normalizedPath) {
    this.normalizedPath = normalizedPath;
  }

  public String selectedPool() {
    return selectedPool;
  }

  public void setSelectedPool(String selectedPool) {
    this.selectedPool = selectedPool;
  }

  public String selectedTargetId() {
    return selectedTargetId;
  }

  public void setSelectedTargetId(String selectedTargetId) {
    this.selectedTargetId = selectedTargetId;
  }

  public int attemptCount() {
    return attemptCount;
  }

  public void incrementAttemptCount() {
    attemptCount++;
  }

  public int retryCount() {
    return retryCount;
  }

  public void incrementRetryCount() {
    retryCount++;
  }

  public boolean fallbackUsed() {
    return fallbackUsed;
  }

  public void setFallbackUsed(boolean fallbackUsed) {
    this.fallbackUsed = fallbackUsed;
  }

  public CircuitBreakerState circuitBreakerState() {
    return circuitBreakerState;
  }

  public void setCircuitBreakerState(CircuitBreakerState circuitBreakerState) {
    this.circuitBreakerState = circuitBreakerState;
  }

  public String healthState() {
    return healthState;
  }

  public void setHealthState(String healthState) {
    this.healthState = healthState;
  }

  public GatewayErrorType errorType() {
    return errorType;
  }

  public void setErrorType(GatewayErrorType errorType) {
    this.errorType = errorType == null ? GatewayErrorType.NONE : errorType;
  }

  public long bytesReceived() {
    return bytesReceived;
  }

  public void setBytesReceived(long bytesReceived) {
    this.bytesReceived = bytesReceived;
  }

  public long bytesSent() {
    return bytesSent;
  }

  public void setBytesSent(long bytesSent) {
    this.bytesSent = bytesSent;
  }

  public boolean clientDisconnected() {
    return clientDisconnected;
  }

  public void setClientDisconnected(boolean clientDisconnected) {
    this.clientDisconnected = clientDisconnected;
  }

  public String snapshotVersion() {
    return snapshotVersion;
  }

  public void setSnapshotVersion(String snapshotVersion) {
    this.snapshotVersion = snapshotVersion;
  }

  public String traceId() {
    return traceId;
  }

  public void setTraceId(String traceId) {
    this.traceId = traceId;
  }

  public String spanId() {
    return spanId;
  }

  public void setSpanId(String spanId) {
    this.spanId = spanId;
  }
}
