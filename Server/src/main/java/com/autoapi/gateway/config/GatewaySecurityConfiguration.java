package com.autoapi.gateway.config;

import com.autoapi.gateway.auth.ApiKeyAuthenticator;
import com.autoapi.gateway.auth.GatewaySecurityMetrics;
import com.autoapi.gateway.redis.GatewayRateLimitService;
import com.autoapi.gateway.security.GatewaySecurityPipeline;
import com.autoapi.runtime.AutoApiRole;
import com.autoapi.runtime.ConditionalOnAutoApiRole;
import com.autoapi.security.ApiKeyPepperProperties;
import com.autoapi.security.ApiKeyPepperValidator;
import com.autoapi.security.ConditionalOnConfiguredApiKeyPepper;
import com.autoapi.web.ErrorResponseWriter;
import org.springframework.beans.factory.ObjectProvider;
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

  @Bean
  GatewaySecurityPipeline gatewaySecurityPipeline(
      ApiKeyAuthenticator apiKeyAuthenticator,
      ObjectProvider<GatewayRateLimitService> rateLimitServiceProvider,
      GatewaySecurityMetrics metrics,
      ErrorResponseWriter errorResponseWriter) {
    return new GatewaySecurityPipeline(
        apiKeyAuthenticator, rateLimitServiceProvider, metrics, errorResponseWriter);
  }
}
