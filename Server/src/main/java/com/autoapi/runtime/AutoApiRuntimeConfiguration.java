package com.autoapi.runtime;

import com.autoapi.gateway.GatewayProperties;
import com.autoapi.security.ApiKeyPepperProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
  AutoApiRuntimeProperties.class,
  GatewayProperties.class,
  ApiKeyPepperProperties.class
})
public class AutoApiRuntimeConfiguration {}
