package com.autoapi.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpMethod;

public final class ConfigLoader {

  private ConfigLoader() {}

  public static RuntimeConfig load(String configPath, ObjectMapper objectMapper) {
    Path path = Path.of(configPath);
    if (!Files.exists(path)) {
      throw new ConfigLoadException("Configuration file does not exist: " + configPath);
    }
    if (!Files.isRegularFile(path)) {
      throw new ConfigLoadException("Configuration path is not a file: " + configPath);
    }
    try {
      JsonNode root = objectMapper.readTree(path.toFile());
      GatewayConfig gateway = parseGateway(root.get("gateway"));
      List<RouteConfig> routes = parseRoutes(root.get("routes"));
      return new RuntimeConfig(gateway, routes);
    } catch (IOException e) {
      throw new ConfigLoadException("Failed to read configuration: " + configPath, e);
    }
  }

  private static GatewayConfig parseGateway(JsonNode node) {
    if (node == null || node.isNull()) {
      throw new ConfigLoadException("Missing required field: gateway");
    }
    String listenAddress = textField(node, "listenAddress", "gateway.listenAddress");
    int port = node.path("port").asInt(-1);
    return new GatewayConfig(listenAddress, port);
  }

  private static List<RouteConfig> parseRoutes(JsonNode node) {
    if (node == null || !node.isArray()) {
      throw new ConfigLoadException("Missing or invalid field: routes (must be an array)");
    }
    List<RouteConfig> routes = new ArrayList<>();
    for (JsonNode routeNode : node) {
      String id = textField(routeNode, "id", "route.id");
      String host = textField(routeNode, "host", "route.host");
      String pathPrefix = textField(routeNode, "pathPrefix", "route.pathPrefix");
      Set<HttpMethod> methods = parseMethods(routeNode.get("methods"));
      URI upstreamUrl = parseUpstreamUrl(routeNode.path("upstream"));
      routes.add(new RouteConfig(id, host, pathPrefix, methods, new UpstreamConfig(upstreamUrl)));
    }
    return routes;
  }

  private static Set<HttpMethod> parseMethods(JsonNode node) {
    if (node == null || !node.isArray() || node.isEmpty()) {
      throw new ConfigLoadException("Route methods must be a non-empty array");
    }
    Set<HttpMethod> methods = new LinkedHashSet<>();
    for (JsonNode methodNode : node) {
      if (!methodNode.isTextual()) {
        throw new ConfigLoadException("Invalid HTTP method entry (expected string)");
      }
      String raw = methodNode.asText().trim().toUpperCase();
      if (raw.isEmpty()) {
        throw new ConfigLoadException("Invalid HTTP method entry (blank)");
      }
      try {
        methods.add(HttpMethod.valueOf(raw));
      } catch (IllegalArgumentException e) {
        throw new ConfigLoadException("Unsupported HTTP method: " + raw);
      }
    }
    return methods;
  }

  private static URI parseUpstreamUrl(JsonNode upstreamNode) {
    if (upstreamNode == null || upstreamNode.isNull()) {
      throw new ConfigLoadException("Route upstream is required");
    }
    String urlText = textField(upstreamNode, "url", "route.upstream.url");
    try {
      return URI.create(urlText);
    } catch (IllegalArgumentException e) {
      throw new ConfigLoadException("Malformed upstream URI: " + urlText, e);
    }
  }

  private static String textField(JsonNode node, String field, String label) {
    JsonNode value = node.get(field);
    if (value == null || !value.isTextual() || value.asText().isBlank()) {
      throw new ConfigLoadException("Missing or blank required field: " + label);
    }
    return value.asText().trim();
  }
}
