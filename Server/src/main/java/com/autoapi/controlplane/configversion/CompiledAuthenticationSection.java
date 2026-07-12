package com.autoapi.controlplane.configversion;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"required"})
public record CompiledAuthenticationSection(boolean required) {}
