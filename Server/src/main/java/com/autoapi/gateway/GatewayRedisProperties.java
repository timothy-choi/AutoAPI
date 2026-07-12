package com.autoapi.gateway;

import java.time.Duration;

public class GatewayRedisProperties {

  private String url = "redis://redis:6379";
  private Duration connectTimeout = Duration.ofSeconds(2);
  private Duration commandTimeout = Duration.ofSeconds(1);

  public String url() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public Duration connectTimeout() {
    return connectTimeout;
  }

  public void setConnectTimeout(Duration connectTimeout) {
    this.connectTimeout = connectTimeout;
  }

  public Duration commandTimeout() {
    return commandTimeout;
  }

  public void setCommandTimeout(Duration commandTimeout) {
    this.commandTimeout = commandTimeout;
  }
}
