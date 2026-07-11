package com.autoapi.controlplane.validation;

import java.util.List;

public record ValidationResult(
    boolean valid, List<ValidationError> errors, String contentHash, ValidationSummary summary) {

  public static ValidationResult valid(String contentHash, ValidationSummary summary) {
    return new ValidationResult(true, List.of(), contentHash, summary);
  }

  public static ValidationResult invalid(List<ValidationError> errors) {
    return new ValidationResult(false, errors, null, null);
  }
}
