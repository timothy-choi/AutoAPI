package com.autoapi.gateway.config.remote;

import com.autoapi.runtime.AutoApiRole;
import com.autoapi.runtime.ConditionalOnAutoApiRole;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnAutoApiRole({AutoApiRole.GATEWAY, AutoApiRole.COMBINED})
@ConditionalOnProperty(name = "autoapi.gateway.config-source", havingValue = "control-plane")
public final class GatewayRegistrationState {

  private final AtomicBoolean registered = new AtomicBoolean(false);

  public boolean isRegistered() {
    return registered.get();
  }

  public void markRegistered() {
    registered.set(true);
  }

  public void markUnregistered() {
    registered.set(false);
  }
}
