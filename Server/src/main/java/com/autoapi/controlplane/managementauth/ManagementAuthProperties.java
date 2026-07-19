package com.autoapi.controlplane.managementauth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "autoapi.management-auth")
public class ManagementAuthProperties {

  private boolean enabled = true;
  private Token token = new Token();
  private Bootstrap bootstrap = new Bootstrap();
  private Authorization authorization = new Authorization();
  private Security security = new Security();

  public boolean enabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public Token token() {
    return token;
  }

  public void setToken(Token token) {
    if (token != null) {
      this.token = token;
    }
  }

  public Bootstrap bootstrap() {
    return bootstrap;
  }

  public Authorization authorization() {
    return authorization;
  }

  public Security security() {
    return security;
  }

  public static class Token {
    private String prefix = "aat";
    private Duration defaultTtl = Duration.ofDays(30);
    private Duration maxTtl = Duration.ofDays(365);
    private int secretBytes = 32;
    private Duration lastUsedUpdateInterval = Duration.ofMinutes(15);
    private String pepper = "";

    public String prefix() {
      return prefix;
    }

    public void setPrefix(String prefix) {
      this.prefix = prefix == null ? "aat" : prefix;
    }

    public Duration defaultTtl() {
      return defaultTtl;
    }

    public void setDefaultTtl(Duration defaultTtl) {
      this.defaultTtl = defaultTtl == null ? Duration.ofDays(30) : defaultTtl;
    }

    public Duration maxTtl() {
      return maxTtl;
    }

    public void setMaxTtl(Duration maxTtl) {
      this.maxTtl = maxTtl == null ? Duration.ofDays(365) : maxTtl;
    }

    public int secretBytes() {
      return secretBytes;
    }

    public void setSecretBytes(int secretBytes) {
      this.secretBytes = secretBytes <= 0 ? 32 : secretBytes;
    }

    public Duration lastUsedUpdateInterval() {
      return lastUsedUpdateInterval;
    }

    public void setLastUsedUpdateInterval(Duration lastUsedUpdateInterval) {
      this.lastUsedUpdateInterval =
          lastUsedUpdateInterval == null ? Duration.ofMinutes(15) : lastUsedUpdateInterval;
    }

    public String pepper() {
      return pepper;
    }

    public void setPepper(String pepper) {
      this.pepper = pepper == null ? "" : pepper;
    }
  }

  public static class Bootstrap {
    private boolean enabled = true;
    private String token = "";
    private boolean allowAfterInitialization = false;

    public boolean enabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public String token() {
      return token;
    }

    public void setToken(String token) {
      this.token = token == null ? "" : token;
    }

    public boolean allowAfterInitialization() {
      return allowAfterInitialization;
    }

    public void setAllowAfterInitialization(boolean allowAfterInitialization) {
      this.allowAfterInitialization = allowAfterInitialization;
    }
  }

  public static class Authorization {
    private boolean defaultDeny = true;

    public boolean defaultDeny() {
      return defaultDeny;
    }

    public void setDefaultDeny(boolean defaultDeny) {
      this.defaultDeny = defaultDeny;
    }
  }

  public static class Security {
    private boolean allowUnauthenticatedDevelopment = false;

    public boolean allowUnauthenticatedDevelopment() {
      return allowUnauthenticatedDevelopment;
    }

    public void setAllowUnauthenticatedDevelopment(boolean allowUnauthenticatedDevelopment) {
      this.allowUnauthenticatedDevelopment = allowUnauthenticatedDevelopment;
    }
  }
}
