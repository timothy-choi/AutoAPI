package com.autoapi.controlplane.configversion;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"keyId", "secretDigest", "enabled", "expiresAt"})
public record CompiledApiKeySection(
    String keyId, String secretDigest, boolean enabled, String expiresAt) {}
