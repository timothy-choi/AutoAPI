package com.autoapi.gateway.config;

import com.autoapi.gateway.health.FailureClassifier;
import com.autoapi.gateway.health.HealthAwareTargetSelector;
import com.autoapi.gateway.health.TargetHealthRegistry;
import com.autoapi.runtime.AutoApiRole;
import com.autoapi.runtime.ConditionalOnAutoApiRole;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnAutoApiRole({AutoApiRole.GATEWAY, AutoApiRole.COMBINED})
public class GatewayHealthAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  Clock gatewayHealthClock() {
    return Clock.systemUTC();
  }

  @Bean
  @ConditionalOnMissingBean
  FailureClassifier failureClassifier() {
    return new FailureClassifier();
  }

  @Bean
  @ConditionalOnMissingBean
  TargetHealthRegistry targetHealthRegistry(Clock gatewayHealthClock) {
    return new TargetHealthRegistry(gatewayHealthClock);
  }

  @Bean
  @ConditionalOnMissingBean
  HealthAwareTargetSelector healthAwareTargetSelector(
      TargetHealthRegistry targetHealthRegistry, Clock gatewayHealthClock) {
    return new HealthAwareTargetSelector(targetHealthRegistry, gatewayHealthClock);
  }
}
