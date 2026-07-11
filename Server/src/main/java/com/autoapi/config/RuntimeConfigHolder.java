package com.autoapi.config;

/** Holds the single immutable runtime configuration loaded at startup. */
public final class RuntimeConfigHolder {

  private final RuntimeConfig config;

  public RuntimeConfigHolder(RuntimeConfig config) {
    this.config = config;
  }

  public RuntimeConfig config() {
    return config;
  }
}
