package com.autoapi.controlplane.managementauth;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = "autoapi.controlplane.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class ManagementAuthMetrics {

  private final Counter authenticationSuccess;
  private final Counter authenticationFailures;
  private final Counter authorizationAllow;
  private final Counter authorizationDeny;

  public ManagementAuthMetrics(MeterRegistry meterRegistry) {
    this.authenticationSuccess =
        Counter.builder("autoapi_management_authentication_total")
            .tag("result", "success")
            .register(meterRegistry);
    this.authenticationFailures =
        Counter.builder("autoapi_management_authentication_failures_total").register(meterRegistry);
    this.authorizationAllow =
        Counter.builder("autoapi_management_authorization_decisions_total")
            .tag("decision", "allow")
            .register(meterRegistry);
    this.authorizationDeny =
        Counter.builder("autoapi_management_authorization_denials_total").register(meterRegistry);
  }

  public void recordAuthenticationSuccess(ManagementPrincipal principal) {
    authenticationSuccess.increment();
  }

  public void recordAuthenticationFailure(String reasonCode) {
    authenticationFailures.increment();
  }

  public void recordAuthorizationDecision(boolean allowed, ManagementPermission permission) {
    if (allowed) {
      authorizationAllow.increment();
    } else {
      authorizationDeny.increment();
    }
  }

  public void recordCredentialCreated() {}

  public void recordCredentialRotated() {}

  public void recordCredentialRevoked() {}
}
