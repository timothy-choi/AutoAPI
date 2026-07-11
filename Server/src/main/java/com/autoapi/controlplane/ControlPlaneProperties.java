package com.autoapi.controlplane;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "autoapi.controlplane")
public record ControlPlaneProperties(boolean enabled, CompiledGatewayProperties compiledGateway) {

  public record CompiledGatewayProperties(String listenAddress, int port) {}
}
