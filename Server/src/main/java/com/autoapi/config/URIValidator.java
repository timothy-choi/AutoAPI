package com.autoapi.config;

import com.autoapi.validation.UpstreamUriValidator;
import java.net.URI;

final class URIValidator {

  private URIValidator() {}

  static void validateUpstream(URI uri, String routeId) {
    UpstreamUriValidator.validate(uri, "route: " + routeId);
  }
}
