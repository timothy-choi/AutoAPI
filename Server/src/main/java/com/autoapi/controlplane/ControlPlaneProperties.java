package com.autoapi.controlplane;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "autoapi.controlplane")
public class ControlPlaneProperties {

  private boolean enabled = true;
  private CompiledGatewayProperties compiledGateway = new CompiledGatewayProperties();
  private Duration gatewayStaleAfter = Duration.ofSeconds(30);

  public boolean enabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public CompiledGatewayProperties compiledGateway() {
    return compiledGateway;
  }

  public void setCompiledGateway(CompiledGatewayProperties compiledGateway) {
    this.compiledGateway = compiledGateway;
  }

  public Duration gatewayStaleAfter() {
    return gatewayStaleAfter;
  }

  public void setGatewayStaleAfter(Duration gatewayStaleAfter) {
    this.gatewayStaleAfter = gatewayStaleAfter;
  }

  public static class CompiledGatewayProperties {
    private String listenAddress = "0.0.0.0";
    private int port = 8080;

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
  }
}
