package com.autoapi.config;

public record RouteConfig(
    String id,
    String host,
    String pathPrefix,
    java.util.Set<org.springframework.http.HttpMethod> methods,
    UpstreamConfig upstream,
    RuntimeAuthentication authentication,
    RuntimeRateLimit rateLimit,
    RuntimeRetryPolicyConfig retry) {

  public RouteConfig(
      String id,
      String host,
      String pathPrefix,
      java.util.Set<org.springframework.http.HttpMethod> methods,
      UpstreamConfig upstream) {
    this(id, host, pathPrefix, methods, upstream, null, null, null);
  }

  public RouteConfig(
      String id,
      String host,
      String pathPrefix,
      java.util.Set<org.springframework.http.HttpMethod> methods,
      UpstreamConfig upstream,
      RuntimeAuthentication authentication,
      RuntimeRateLimit rateLimit) {
    this(id, host, pathPrefix, methods, upstream, authentication, rateLimit, null);
  }

  public RouteConfig {
    methods = java.util.Set.copyOf(methods);
  }

  public boolean authenticationRequired() {
    return authentication != null && authentication.required();
  }

  public boolean rateLimitEnabled() {
    return rateLimit != null;
  }

  public boolean retryEnabled() {
    return retry != null && retry.retriesEnabled();
  }
}
