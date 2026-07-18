package com.autoapi.controlplane.events.webhooks;

import com.autoapi.controlplane.api.ControlPlaneException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;

public final class WebhookUrlValidator {

  private WebhookUrlValidator() {}

  public static URI validate(String url, WebhooksProperties.Security security) {
    if (url == null || url.isBlank()) {
      throw ControlPlaneException.invalidRequest("Webhook URL is required");
    }
    URI uri;
    try {
      uri = URI.create(url.trim());
    } catch (IllegalArgumentException ex) {
      throw ControlPlaneException.invalidRequest("Webhook URL is invalid");
    }
    String scheme = uri.getScheme();
    if (scheme == null) {
      throw ControlPlaneException.invalidRequest("Webhook URL must include a scheme");
    }
    scheme = scheme.toLowerCase(Locale.ROOT);
    if (security.requireHttps() && !"https".equals(scheme)) {
      if (!("http".equals(scheme) && security.allowLoopback())) {
        throw ControlPlaneException.invalidRequest("Webhook URL must use HTTPS");
      }
    }
    if (!"https".equals(scheme) && !"http".equals(scheme)) {
      throw ControlPlaneException.invalidRequest("Unsupported webhook URL scheme");
    }
    if (uri.getUserInfo() != null) {
      throw ControlPlaneException.invalidRequest("Webhook URL must not contain credentials");
    }
    if (uri.getHost() == null || uri.getHost().isBlank()) {
      throw ControlPlaneException.invalidRequest("Webhook URL must include a host");
    }
    validateResolvedAddress(uri.getHost(), security);
    int port = uri.getPort();
    if (port != -1 && (port < 1 || port > 65535)) {
      throw ControlPlaneException.invalidRequest("Webhook URL port is invalid");
    }
    if (port != -1 && port < 1024 && !security.allowLoopback()) {
      throw ControlPlaneException.invalidRequest("Webhook URL port is not allowed");
    }
    return uri;
  }

  private static void validateResolvedAddress(String host, WebhooksProperties.Security security) {
    if (security.allowLoopback() && isLoopbackHost(host)) {
      return;
    }
    if (security.allowPrivateAddresses()) {
      return;
    }
    try {
      InetAddress[] addresses = InetAddress.getAllByName(host);
      for (InetAddress address : addresses) {
        if (address.isAnyLocalAddress()
            || address.isLoopbackAddress()
            || address.isLinkLocalAddress()
            || address.isSiteLocalAddress()
            || address.isMulticastAddress()) {
          throw ControlPlaneException.invalidRequest("Webhook URL resolves to a blocked address");
        }
      }
    } catch (UnknownHostException ex) {
      throw ControlPlaneException.invalidRequest("Webhook URL host could not be resolved");
    }
  }

  private static boolean isLoopbackHost(String host) {
    return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host);
  }
}
