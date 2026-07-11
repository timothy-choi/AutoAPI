package com.autoapi.runtime;

import com.autoapi.gateway.GatewayProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({AutoApiRuntimeProperties.class, GatewayProperties.class})
public class AutoApiRuntimeConfiguration {}
