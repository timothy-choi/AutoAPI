package com.autoapi.config;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

final class URIValidator {

  private static final Set<String> ALLOWED = Set.of("http", "https");

  private URIValidator() {}

  static void validateUpstream(URI uri, String routeId) {
    if (uri == null) {
      throw new ConfigLoadException("Upstream URI missing for route: " + routeId);
    }
    String scheme = uri.getScheme();
    if (scheme == null || !ALLOWED.contains(scheme.toLowerCase(Locale.ROOT))) {
      throw new ConfigLoadException(
          "Upstream URI scheme must be http or https for route: " + routeId);
    }
    if (uri.getHost() == null || uri.getHost().isBlank()) {
      throw new ConfigLoadException("Upstream URI host missing for route: " + routeId);
    }
    if (uri.getUserInfo() != null) {
      throw new ConfigLoadException(
          "Upstream URI must not contain user-info credentials for route: " + routeId);
    }
    if (uri.getQuery() != null && !uri.getQuery().isEmpty()) {
      throw new ConfigLoadException(
          "Upstream URI must not contain a query string for route: " + routeId);
    }
    if (uri.getFragment() != null && !uri.getFragment().isEmpty()) {
      throw new ConfigLoadException(
          "Upstream URI must not contain a fragment for route: " + routeId);
    }
    String path = uri.getPath();
    if (path != null && !path.isEmpty() && !"/".equals(path)) {
      throw new ConfigLoadException(
          "Upstream URI must identify an origin only (no path beyond '/') for route: "
              + routeId
              + "; got path '"
              + path
              + "'");
    }
  }
}
