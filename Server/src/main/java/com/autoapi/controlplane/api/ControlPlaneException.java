package com.autoapi.controlplane.api;

import com.autoapi.controlplane.validation.ValidationError;
import java.util.List;
import org.springframework.http.HttpStatus;

public final class ControlPlaneException extends RuntimeException {

  private final String code;
  private final HttpStatus status;
  private final List<ValidationError> validationErrors;
  private final ExistingConfigVersion existingConfigVersion;

  private ControlPlaneException(
      String code,
      String message,
      HttpStatus status,
      List<ValidationError> validationErrors,
      ExistingConfigVersion existingConfigVersion) {
    super(message);
    this.code = code;
    this.status = status;
    this.validationErrors = validationErrors;
    this.existingConfigVersion = existingConfigVersion;
  }

  public static ControlPlaneException notFound(String message) {
    return new ControlPlaneException(
        "RESOURCE_NOT_FOUND", message, HttpStatus.NOT_FOUND, List.of(), null);
  }

  public static ControlPlaneException conflict(String message) {
    return new ControlPlaneException(
        "RESOURCE_CONFLICT", message, HttpStatus.CONFLICT, List.of(), null);
  }

  public static ControlPlaneException invalidRequest(String message) {
    return new ControlPlaneException(
        "INVALID_REQUEST", message, HttpStatus.BAD_REQUEST, List.of(), null);
  }

  public static ControlPlaneException validationFailed(List<ValidationError> errors) {
    return new ControlPlaneException(
        "CONFIG_VALIDATION_FAILED",
        "Configuration validation failed",
        HttpStatus.UNPROCESSABLE_ENTITY,
        errors,
        null);
  }

  public static ControlPlaneException configVersionAlreadyExists(
      com.autoapi.controlplane.persistence.ConfigVersionEntity existing) {
    ExistingConfigVersion metadata =
        existing == null
            ? null
            : new ExistingConfigVersion(
                existing.id(), existing.apiId(), existing.version(), existing.contentHash());
    return new ControlPlaneException(
        "CONFIG_VERSION_ALREADY_EXISTS",
        "An identical configuration version already exists",
        HttpStatus.CONFLICT,
        List.of(),
        metadata);
  }

  public static ControlPlaneException internal(String message) {
    return new ControlPlaneException(
        "INTERNAL_CONTROL_PLANE_ERROR", message, HttpStatus.INTERNAL_SERVER_ERROR, List.of(), null);
  }

  public String code() {
    return code;
  }

  public HttpStatus status() {
    return status;
  }

  public List<ValidationError> validationErrors() {
    return validationErrors;
  }

  public ExistingConfigVersion existingConfigVersion() {
    return existingConfigVersion;
  }

  public record ExistingConfigVersion(
      java.util.UUID id, java.util.UUID apiId, long version, String contentHash) {}
}
