package com.autoapi.runtime;

import java.util.Arrays;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.ConfigurationCondition;
import org.springframework.core.type.AnnotatedTypeMetadata;

class OnAutoApiRoleCondition implements ConfigurationCondition {

  @Override
  public ConfigurationPhase getConfigurationPhase() {
    return ConfigurationPhase.REGISTER_BEAN;
  }

  @Override
  public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
    AutoApiRole[] required =
        (AutoApiRole[])
            metadata.getAnnotationAttributes(ConditionalOnAutoApiRole.class.getName()).get("value");
    AutoApiRole role =
        Binder.get(context.getEnvironment())
            .bind("autoapi.role", AutoApiRole.class)
            .orElse(AutoApiRole.COMBINED);
    return Arrays.asList(required).contains(role);
  }
}
