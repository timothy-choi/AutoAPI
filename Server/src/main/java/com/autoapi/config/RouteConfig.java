package com.autoapi.config;

import java.util.Set;
import org.springframework.http.HttpMethod;

public record RouteConfig(
    String id, String host, String pathPrefix, Set<HttpMethod> methods, UpstreamConfig upstream) {

  public RouteConfig {
    methods = Set.copyOf(methods);
  }
}
