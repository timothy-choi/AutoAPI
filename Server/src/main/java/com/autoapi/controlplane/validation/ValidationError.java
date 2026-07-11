package com.autoapi.controlplane.validation;

import java.util.UUID;

public record ValidationError(String code, UUID resourceId, String message) {}
