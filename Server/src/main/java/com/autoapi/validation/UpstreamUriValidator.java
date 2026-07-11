package com.autoapi.validation;

import com.autoapi.config.ConfigLoadException;
import java.net.URI;
import java.util.Locale;
import java.util.Set;

/** Validates upstream URIs as origin-only HTTP/HTTPS endpoints. */
public final class UpstreamUriValidator {

  private static final Set<String> ALLOWED = Set.of("http", "https");

  private UpstreamUriValidator() {}

  public static void validate(URI uri, String context) {
    if (uri == null) {
      throw new ConfigLoadException("Upstream URI missing for " + context);
    }
    String scheme = uri.getScheme();
    if (scheme == null || !ALLOWED.contains(scheme.toLowerCase(Locale.ROOT))) {
      throw new ConfigLoadException("Upstream URI scheme must be http or https for " + context);
    }
    if (uri.getHost() == null || uri.getHost().isBlank()) {
      throw new ConfigLoadException("Upstream URI host missing for " + context);
    }
    if (uri.getUserInfo() != null) {
      throw new ConfigLoadException(
          "Upstream URI must not contain user-info credentials for " + context);
    }
    if (uri.getQuery() != null && !uri.getQuery().isEmpty()) {
      throw new ConfigLoadException("Upstream URI must not contain a query string for " + context);
    }
    if (uri.getFragment() != null && !uri.getFragment().isEmpty()) {
      throw new ConfigLoadException("Upstream URI must not contain a fragment for " + context);
    }
    String path = uri.getPath();
    if (path != null && !path.isEmpty() && !"/".equals(path)) {
      throw new ConfigLoadException(
          "Upstream URI must identify an origin only (no path beyond '/') for "
              + context
              + "; got path '"
              + path
              + "'");
    }
  }
}
