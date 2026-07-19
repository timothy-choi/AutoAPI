package com.autoapi.controlplane;

import com.autoapi.controlplane.managementauth.ManagementAuthProperties;
import com.autoapi.runtime.AutoApiRole;
import com.autoapi.runtime.ConditionalOnAutoApiRole;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@ConditionalOnAutoApiRole({AutoApiRole.CONTROL_PLANE, AutoApiRole.COMBINED})
@ConditionalOnProperty(
    name = "autoapi.controlplane.enabled",
    havingValue = "true",
    matchIfMissing = true)
@EnableR2dbcRepositories(basePackages = "com.autoapi.controlplane.persistence")
@EnableConfigurationProperties({ControlPlaneProperties.class, ManagementAuthProperties.class})
@EnableTransactionManagement
public class ControlPlaneConfiguration {}
