package com.autoapi.gateway.config;

import com.autoapi.gateway.auth.ApiKeyAuthenticator;
import com.autoapi.runtime.AutoApiRole;
import com.autoapi.runtime.ConditionalOnAutoApiRole;
import com.autoapi.security.ApiKeyPepperProperties;
import com.autoapi.security.ApiKeyPepperValidator;
import com.autoapi.security.ConditionalOnConfiguredApiKeyPepper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;

@Configuration
@ConditionalOnAutoApiRole({AutoApiRole.GATEWAY, AutoApiRole.COMBINED})
@ConditionalOnClass(ReactiveRedisConnectionFactory.class)
@ConditionalOnConfiguredApiKeyPepper
public class GatewaySecurityConfiguration {

  @Bean
  ApiKeyAuthenticator apiKeyAuthenticator(ApiKeyPepperProperties pepperProperties) {
    ApiKeyPepperValidator.requireConfigured(pepperProperties.apiKeyPepper());
    return new ApiKeyAuthenticator(pepperProperties.apiKeyPepper());
  }
}
