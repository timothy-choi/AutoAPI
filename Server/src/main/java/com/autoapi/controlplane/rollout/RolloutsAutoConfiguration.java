package com.autoapi.controlplane.rollout;

import com.autoapi.controlplane.persistence.RuntimeRolloutRepositoryCustom;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RolloutsProperties.class)
@ConditionalOnProperty(
    name = {"autoapi.controlplane.enabled", "autoapi.rollouts.enabled"},
    havingValue = "true",
    matchIfMissing = true)
public class RolloutsAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(name = "rolloutsClock")
  Clock rolloutsClock() {
    return Clock.systemUTC();
  }

  @Bean
  @ConditionalOnProperty(
      name = {"autoapi.controlplane.enabled", "autoapi.rollouts.enabled"},
      havingValue = "true",
      matchIfMissing = true)
  RuntimeRolloutReconciler runtimeRolloutReconciler(
      RolloutsProperties properties,
      RuntimeRolloutRepositoryCustom rolloutRepository,
      RuntimeRolloutService rolloutService,
      com.autoapi.controlplane.ControlPlaneProperties controlPlaneProperties,
      RolloutsMetrics metrics,
      Clock rolloutsClock) {
    return new RuntimeRolloutReconciler(
        properties,
        rolloutRepository,
        rolloutService,
        controlPlaneProperties,
        metrics,
        rolloutsClock);
  }
}
