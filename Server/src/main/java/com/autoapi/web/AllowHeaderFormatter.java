package com.autoapi.web;

import java.util.Comparator;
import java.util.stream.Collectors;
import org.springframework.http.HttpMethod;

final class AllowHeaderFormatter {

  private AllowHeaderFormatter() {}

  static String format(java.util.Set<HttpMethod> methods) {
    return methods.stream()
        .map(HttpMethod::name)
        .sorted(Comparator.naturalOrder())
        .collect(Collectors.joining(", "));
  }
}
