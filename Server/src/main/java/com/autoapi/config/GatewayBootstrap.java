package com.autoapi.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

public final class GatewayBootstrap {

  private GatewayBootstrap() {}

  public static RuntimeConfig loadAndValidate(String configPath, ObjectMapper objectMapper) {
    RuntimeConfig runtimeConfig = ConfigLoader.load(configPath, objectMapper);
    ConfigValidator.validate(runtimeConfig);
    System.setProperty("autoapi.config.path", configPath);
    return runtimeConfig;
  }

  public static RuntimeConfig loadValidateAndApply(String[] args, ObjectMapper objectMapper) {
    String configPath = ConfigPathResolver.resolve(args);
    RuntimeConfig runtimeConfig = loadAndValidate(configPath, objectMapper);
    applyListenProperties(runtimeConfig);
    return runtimeConfig;
  }

  public static void applyListenProperties(RuntimeConfig runtimeConfig) {
    System.setProperty("server.port", String.valueOf(runtimeConfig.gateway().port()));
    System.setProperty("server.address", runtimeConfig.gateway().listenAddress());
  }

  public static void applyGatewayListenFromEnvironment() {
    String port = System.getenv().getOrDefault("AUTOAPI_GATEWAY_PORT", "8080");
    String address = System.getenv().getOrDefault("AUTOAPI_GATEWAY_LISTEN_ADDRESS", "0.0.0.0");
    System.setProperty("server.port", port);
    System.setProperty("server.address", address);
  }

  public static ApplicationContextInitializer<ConfigurableApplicationContext> initializer(
      RuntimeConfig runtimeConfig) {
    return context -> {
      context.getBeanFactory().registerSingleton("runtimeConfig", runtimeConfig);
      context
          .getBeanFactory()
          .registerSingleton("runtimeConfigHolder", new RuntimeConfigHolder(runtimeConfig));
    };
  }
}
