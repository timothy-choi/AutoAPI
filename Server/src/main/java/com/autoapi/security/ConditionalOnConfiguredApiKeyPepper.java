package com.autoapi.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(ConditionalOnConfiguredApiKeyPepper.OnConfiguredApiKeyPepperCondition.class)
public @interface ConditionalOnConfiguredApiKeyPepper {

  class OnConfiguredApiKeyPepperCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
      String pepper = context.getEnvironment().getProperty("autoapi.security.api-key-pepper", "");
      return StringUtils.hasText(pepper)
          && pepper.length() >= ApiKeyPepperProperties.MIN_PEPPER_LENGTH;
    }
  }
}
