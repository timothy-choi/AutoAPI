package com.autoapi.gateway.config;

import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

@Component
public class ActiveRuntimeConfigHolder {

  private final AtomicReference<ActiveRuntimeBundle> active = new AtomicReference<>();

  public boolean hasActiveConfig() {
    return active.get() != null;
  }

  public ActiveRuntimeBundle getActive() {
    return active.get();
  }

  public void activate(ActiveRuntimeBundle bundle) {
    active.set(bundle);
  }

  public ActiveRuntimeBundle getActiveForRequest() {
    return active.get();
  }
}
