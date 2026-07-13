package com.autoapi.config;

import java.net.URI;
import java.util.UUID;

public record UpstreamTargetReference(UUID targetId, URI url, int weight) {}
