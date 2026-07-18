package com.autoapi.gateway.observability;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "autoapi.gateway.observability")
public class GatewayObservabilityProperties {

  private boolean tracingEnabled = true;
  private double traceSampleRate = 1.0d;
  private String otlpEndpoint = "";
  private String serviceName = "autoapi-gateway";
  private int requestSummaryBufferSize = 200;
  private int requestSummaryExportBatchSize = 10;

  public boolean tracingEnabled() {
    return tracingEnabled;
  }

  public void setTracingEnabled(boolean tracingEnabled) {
    this.tracingEnabled = tracingEnabled;
  }

  public double traceSampleRate() {
    return traceSampleRate;
  }

  public void setTraceSampleRate(double traceSampleRate) {
    this.traceSampleRate = traceSampleRate;
  }

  public String otlpEndpoint() {
    return otlpEndpoint;
  }

  public void setOtlpEndpoint(String otlpEndpoint) {
    this.otlpEndpoint = otlpEndpoint == null ? "" : otlpEndpoint;
  }

  public String serviceName() {
    return serviceName;
  }

  public void setServiceName(String serviceName) {
    this.serviceName = serviceName;
  }

  public int requestSummaryBufferSize() {
    return requestSummaryBufferSize;
  }

  public void setRequestSummaryBufferSize(int requestSummaryBufferSize) {
    this.requestSummaryBufferSize = requestSummaryBufferSize;
  }

  public int requestSummaryExportBatchSize() {
    return requestSummaryExportBatchSize;
  }

  public void setRequestSummaryExportBatchSize(int requestSummaryExportBatchSize) {
    this.requestSummaryExportBatchSize = requestSummaryExportBatchSize;
  }
}
