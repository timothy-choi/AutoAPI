package com.autoapi.gateway.config;

import com.autoapi.config.RuntimeConfig;
import com.autoapi.config.RuntimeConfigHolder;
import com.autoapi.runtime.AutoApiRole;
import com.autoapi.runtime.ConditionalOnAutoApiRole;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnAutoApiRole({AutoApiRole.GATEWAY, AutoApiRole.COMBINED})
@ConditionalOnProperty(
    name = "autoapi.gateway.config-source",
    havingValue = "static",
    matchIfMissing = true)
public class StaticActiveConfigBootstrap {

  private final RuntimeConfigHolder runtimeConfigHolder;
  private final ActiveRuntimeConfigHolder activeRuntimeConfigHolder;

  public StaticActiveConfigBootstrap(
      RuntimeConfigHolder runtimeConfigHolder,
      ActiveRuntimeConfigHolder activeRuntimeConfigHolder) {
    this.runtimeConfigHolder = runtimeConfigHolder;
    this.activeRuntimeConfigHolder = activeRuntimeConfigHolder;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void activateStaticConfig() {
    RuntimeConfig config = runtimeConfigHolder.config();
    activeRuntimeConfigHolder.activate(new ActiveRuntimeBundle(null, 0, "static-file", config));
  }
}
