package com.autoapi;

import com.autoapi.config.GatewayBootstrap;
import com.autoapi.config.RuntimeConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class AutoApiApplication {

  public static void main(String[] args) {
    ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    RuntimeConfig runtimeConfig = GatewayBootstrap.loadValidateAndApply(args, objectMapper);
    SpringApplication app = new SpringApplication(AutoApiApplication.class);
    app.addInitializers(GatewayBootstrap.initializer(runtimeConfig));
    app.run(args);
  }

  @Bean
  ObjectMapper objectMapper() {
    return new ObjectMapper().findAndRegisterModules();
  }
}
