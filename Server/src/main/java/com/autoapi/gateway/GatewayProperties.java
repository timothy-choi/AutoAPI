package com.autoapi.gateway;

import java.time.Duration;
import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "autoapi.gateway")
public class GatewayProperties {

  private GatewayConfigSource configSource = GatewayConfigSource.STATIC;
  private UUID apiId;
  private String controlPlaneBaseUrl = "http://control-plane:8080";
  private Duration pollInterval = Duration.ofSeconds(5);
  private Duration initialLoadTimeout = Duration.ofSeconds(30);
  private String listenAddress = "0.0.0.0";
  private int port = 8080;
  private ControlPlaneClientProperties controlPlaneClient = new ControlPlaneClientProperties();

  public GatewayConfigSource configSource() {
    return configSource;
  }

  public void setConfigSource(GatewayConfigSource configSource) {
    this.configSource = configSource;
  }

  public UUID apiId() {
    return apiId;
  }

  public void setApiId(UUID apiId) {
    this.apiId = apiId;
  }

  public String controlPlaneBaseUrl() {
    return controlPlaneBaseUrl;
  }

  public void setControlPlaneBaseUrl(String controlPlaneBaseUrl) {
    this.controlPlaneBaseUrl = controlPlaneBaseUrl;
  }

  public Duration pollInterval() {
    return pollInterval;
  }

  public void setPollInterval(Duration pollInterval) {
    this.pollInterval = pollInterval;
  }

  public Duration initialLoadTimeout() {
    return initialLoadTimeout;
  }

  public void setInitialLoadTimeout(Duration initialLoadTimeout) {
    this.initialLoadTimeout = initialLoadTimeout;
  }

  public String listenAddress() {
    return listenAddress;
  }

  public void setListenAddress(String listenAddress) {
    this.listenAddress = listenAddress;
  }

  public int port() {
    return port;
  }

  public void setPort(int port) {
    this.port = port;
  }

  public ControlPlaneClientProperties controlPlaneClient() {
    return controlPlaneClient;
  }

  public void setControlPlaneClient(ControlPlaneClientProperties controlPlaneClient) {
    this.controlPlaneClient = controlPlaneClient;
  }
}
