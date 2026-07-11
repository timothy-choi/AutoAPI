package com.autoapi.controlplane.configversion;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"listenAddress", "port"})
public record CompiledGatewaySection(String listenAddress, int port) {}
