package com.autoapi.runtime;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "autoapi")
public class AutoApiRuntimeProperties {

  private AutoApiRole role = AutoApiRole.COMBINED;

  public AutoApiRole role() {
    return role;
  }

  public void setRole(AutoApiRole role) {
    this.role = role;
  }
}
