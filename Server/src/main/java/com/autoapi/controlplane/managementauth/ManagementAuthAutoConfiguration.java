package com.autoapi.controlplane.managementauth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(
    name = "autoapi.controlplane.enabled",
    havingValue = "true",
    matchIfMissing = true)
@EnableConfigurationProperties(ManagementAuthProperties.class)
public class ManagementAuthAutoConfiguration {}
