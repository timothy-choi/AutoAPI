package com.autoapi.gateway;

import java.time.Duration;
import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "autoapi.gateway")
public class GatewayProperties {

  private GatewayConfigSource configSource = GatewayConfigSource.STATIC;
  private String gatewayId;
  private String gatewayGroup = "default";
  private UUID apiId;
  private String controlPlaneBaseUrl = "http://control-plane:8080";
  private Duration pollInterval = Duration.ofSeconds(5);
  private Duration initialLoadTimeout = Duration.ofSeconds(30);
  private Duration heartbeatInterval = Duration.ofSeconds(10);
  private Duration heartbeatTimeout = Duration.ofSeconds(3);
  private String listenAddress = "0.0.0.0";
  private int port = 8080;
  private ControlPlaneClientProperties controlPlaneClient = new ControlPlaneClientProperties();
  private GatewayRedisProperties redis = new GatewayRedisProperties();

  public GatewayConfigSource configSource() {
    return configSource;
  }

  public void setConfigSource(GatewayConfigSource configSource) {
    this.configSource = configSource;
  }

  public String gatewayId() {
    return gatewayId;
  }

  public void setGatewayId(String gatewayId) {
    this.gatewayId = gatewayId;
  }

  /**
   * Binds {@code autoapi.gateway.id} from {@code application.yml} and {@code AUTOAPI_GATEWAY_ID}.
   */
  public void setId(String id) {
    this.gatewayId = id;
  }

  public String gatewayGroup() {
    return gatewayGroup;
  }

  public void setGatewayGroup(String gatewayGroup) {
    this.gatewayGroup = gatewayGroup;
  }

  /**
   * Binds {@code autoapi.gateway.group} from {@code application.yml} and {@code
   * AUTOAPI_GATEWAY_GROUP}.
   */
  public void setGroup(String group) {
    this.gatewayGroup = group;
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

  public Duration heartbeatInterval() {
    return heartbeatInterval;
  }

  public void setHeartbeatInterval(Duration heartbeatInterval) {
    this.heartbeatInterval = heartbeatInterval;
  }

  public Duration heartbeatTimeout() {
    return heartbeatTimeout;
  }

  public void setHeartbeatTimeout(Duration heartbeatTimeout) {
    this.heartbeatTimeout = heartbeatTimeout;
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

  public GatewayRedisProperties redis() {
    return redis;
  }

  public void setRedis(GatewayRedisProperties redis) {
    this.redis = redis;
  }
}
