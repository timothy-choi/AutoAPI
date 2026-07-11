package com.autoapi.web;

import com.autoapi.config.RuntimeConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(RuntimeConfig.class)
public class StartupLogger {

  private static final Logger log = LoggerFactory.getLogger(StartupLogger.class);

  private final RuntimeConfig runtimeConfig;
  private final Environment environment;

  public StartupLogger(RuntimeConfig runtimeConfig, Environment environment) {
    this.runtimeConfig = runtimeConfig;
    this.environment = environment;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void logStartup() {
    String configPath = environment.getProperty("autoapi.config.path", "unknown");
    log.info(
        "AutoAPI gateway started configPath={} listenAddress={} port={} routeCount={}",
        configPath,
        runtimeConfig.gateway().listenAddress(),
        runtimeConfig.gateway().port(),
        runtimeConfig.routes().size());
  }
}
