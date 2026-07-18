package com.autoapi.controlplane.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class DiscoveryPropertiesTest {

  @Test
  void defaultsAreValid() {
    DiscoveryProperties properties =
        new DiscoveryProperties(
            true,
            Duration.ofSeconds(10),
            Duration.ofSeconds(30),
            Duration.ofSeconds(5),
            Duration.ofMinutes(5),
            Duration.ofSeconds(5),
            100,
            Duration.ofHours(24),
            true);
    assertThat(properties.enabled()).isTrue();
    assertThat(properties.staleReaperBatchSize()).isEqualTo(100);
  }
}
