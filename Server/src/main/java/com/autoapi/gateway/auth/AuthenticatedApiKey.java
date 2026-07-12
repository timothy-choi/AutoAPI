package com.autoapi.gateway.auth;

import java.util.UUID;

public record AuthenticatedApiKey(String keyId, UUID apiId) {}
