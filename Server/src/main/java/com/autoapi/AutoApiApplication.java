package com.autoapi;

import com.autoapi.config.GatewayBootstrap;
import com.autoapi.config.RuntimeConfig;
import com.autoapi.gateway.GatewayConfigSource;
import com.autoapi.runtime.AutoApiRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class AutoApiApplication {

  public static void main(String[] args) {
    ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    AutoApiRole role =
        AutoApiRole.parse(resolveProperty("AUTOAPI_ROLE", "autoapi.role", "combined"));
    GatewayConfigSource configSource =
        GatewayConfigSource.parse(
            resolveProperty(
                "AUTOAPI_GATEWAY_CONFIG_SOURCE", "autoapi.gateway.config-source", "static"));

    SpringApplication app = new SpringApplication(AutoApiApplication.class);

    if (role.runsGateway()) {
      if (configSource == GatewayConfigSource.STATIC) {
        RuntimeConfig runtimeConfig = GatewayBootstrap.loadValidateAndApply(args, objectMapper);
        app.addInitializers(GatewayBootstrap.initializer(runtimeConfig));
      } else {
        GatewayBootstrap.applyGatewayListenFromEnvironment();
      }
    }

    app.run(args);
  }

  private static String resolveProperty(
      String envName, String systemProperty, String defaultValue) {
    String env = System.getenv(envName);
    if (env != null && !env.isBlank()) {
      return env;
    }
    String property = System.getProperty(systemProperty);
    if (property != null && !property.isBlank()) {
      return property;
    }
    return defaultValue;
  }

  @Bean
  ObjectMapper objectMapper() {
    return new ObjectMapper().findAndRegisterModules();
  }
}
