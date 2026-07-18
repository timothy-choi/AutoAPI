package com.autoapi.controlplane.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ServiceMetadataValidatorTest {

  @Test
  void acceptsBoundedMetadata() {
    String json =
        ServiceMetadataValidator.normalizeOrEmpty(Map.of("version", "1", "role", "worker"));
    assertThat(json).contains("version");
  }

  @Test
  void rejectsSecretLikeKeys() {
    assertThatThrownBy(
            () -> ServiceMetadataValidator.normalizeOrEmpty(Map.of("api_token", "value")))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
