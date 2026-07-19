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

  public static ControlPlaneException unauthorized(String message) {
    return new ControlPlaneException(
        "UNAUTHORIZED", message, HttpStatus.UNAUTHORIZED, List.of(), null);
  }

  public static ControlPlaneException authenticationRequired(String message) {
    return new ControlPlaneException(
        "AUTHENTICATION_REQUIRED", message, HttpStatus.UNAUTHORIZED, List.of(), null);
  }

  public static ControlPlaneException invalidCredential(String message) {
    return new ControlPlaneException(
        "INVALID_CREDENTIAL", message, HttpStatus.UNAUTHORIZED, List.of(), null);
  }

  public static ControlPlaneException credentialExpired(String message) {
    return new ControlPlaneException(
        "CREDENTIAL_EXPIRED", message, HttpStatus.UNAUTHORIZED, List.of(), null);
  }

  public static ControlPlaneException credentialRevoked(String message) {
    return new ControlPlaneException(
        "CREDENTIAL_REVOKED", message, HttpStatus.UNAUTHORIZED, List.of(), null);
  }

  public static ControlPlaneException principalDisabled(String message) {
    return new ControlPlaneException(
        "PRINCIPAL_DISABLED", message, HttpStatus.UNAUTHORIZED, List.of(), null);
  }

  public static ControlPlaneException forbidden(String message) {
    return new ControlPlaneException(
        "PERMISSION_DENIED", message, HttpStatus.FORBIDDEN, List.of(), null);
  }

  public static ControlPlaneException resourceNotAccessible(String message) {
    return new ControlPlaneException(
        "RESOURCE_NOT_ACCESSIBLE", message, HttpStatus.NOT_FOUND, List.of(), null);
  }

  public static ControlPlaneException delegationDenied(String message) {
    return new ControlPlaneException(
        "DELEGATION_DENIED", message, HttpStatus.FORBIDDEN, List.of(), null);
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

  public static ControlPlaneException configVersionNotFound(String message) {
    return new ControlPlaneException(
        "CONFIG_VERSION_NOT_FOUND", message, HttpStatus.NOT_FOUND, List.of(), null);
  }

  public static ControlPlaneException desiredVersionConflict(String message) {
    return new ControlPlaneException(
        "DESIRED_VERSION_CONFLICT", message, HttpStatus.CONFLICT, List.of(), null);
  }

  public static ControlPlaneException configVersionNotActivatable(String message) {
    return new ControlPlaneException(
        "CONFIG_VERSION_NOT_ACTIVATABLE",
        message,
        HttpStatus.UNPROCESSABLE_ENTITY,
        List.of(),
        null);
  }

  public static ControlPlaneException desiredConfigNotSet(String message) {
    return new ControlPlaneException(
        "DESIRED_CONFIG_NOT_SET", message, HttpStatus.NOT_FOUND, List.of(), null);
  }

  public static ControlPlaneException gatewayNotRegistered(String message) {
    return new ControlPlaneException(
        "GATEWAY_NOT_REGISTERED", message, HttpStatus.NOT_FOUND, List.of(), null);
  }

  public static ControlPlaneException invalidGatewayId(String message) {
    return new ControlPlaneException(
        "INVALID_GATEWAY_ID", message, HttpStatus.BAD_REQUEST, List.of(), null);
  }

  public static ControlPlaneException invalidHeartbeat(String message) {
    return new ControlPlaneException(
        "INVALID_HEARTBEAT", message, HttpStatus.BAD_REQUEST, List.of(), null);
  }

  public static ControlPlaneException invalidConfigStatus(String message) {
    return new ControlPlaneException(
        "INVALID_CONFIG_STATUS", message, HttpStatus.BAD_REQUEST, List.of(), null);
  }

  public static ControlPlaneException contentHashMismatch(String message) {
    return new ControlPlaneException(
        "CONTENT_HASH_MISMATCH", message, HttpStatus.BAD_REQUEST, List.of(), null);
  }

  public static ControlPlaneException reportIdConflict(String message) {
    return new ControlPlaneException(
        "REPORT_ID_CONFLICT", message, HttpStatus.CONFLICT, List.of(), null);
  }

  public static ControlPlaneException diagnosticTooLong(String message) {
    return new ControlPlaneException(
        "DIAGNOSTIC_TOO_LONG", message, HttpStatus.BAD_REQUEST, List.of(), null);
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
