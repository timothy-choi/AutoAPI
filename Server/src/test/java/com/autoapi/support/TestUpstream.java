package com.autoapi.support;

import com.autoapi.config.GatewayBootstrap;
import com.autoapi.config.RuntimeConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

public final class TestUpstream {

  private final HttpServer server;
  private final int port;
  private final AtomicReference<String> lastPath = new AtomicReference<>("");
  private final AtomicReference<String> lastRequestId = new AtomicReference<>("");
  private final AtomicReference<String> lastHost = new AtomicReference<>("");
  private final AtomicReference<String> lastForwardedHost = new AtomicReference<>("");

  private TestUpstream(HttpServer server, int port) {
    this.server = server;
    this.port = port;
  }

  public static TestUpstream start() throws IOException {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    TestUpstream upstream = new TestUpstream(server, server.getAddress().getPort());
    server.createContext(
        "/",
        exchange -> {
          upstream.lastPath.set(exchange.getRequestURI().getPath());
          upstream.lastRequestId.set(exchange.getRequestHeaders().getFirst("X-Request-ID"));
          upstream.lastHost.set(exchange.getRequestHeaders().getFirst("Host"));
          upstream.lastForwardedHost.set(exchange.getRequestHeaders().getFirst("X-Forwarded-Host"));
          byte[] body =
              ("{\"path\":\""
                      + exchange.getRequestURI().getPath()
                      + "\",\"requestId\":\""
                      + upstream.lastRequestId.get()
                      + "\",\"receivedHost\":\""
                      + escapeJson(upstream.lastHost.get())
                      + "\",\"receivedForwardedHost\":\""
                      + escapeJson(upstream.lastForwardedHost.get())
                      + "\"}")
                  .getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, body.length);
          try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
          }
        });
    server.start();
    return upstream;
  }

  private static String escapeJson(String value) {
    if (value == null) {
      return "";
    }
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  public int port() {
    return port;
  }

  public String lastPath() {
    return lastPath.get();
  }

  public String lastRequestId() {
    return lastRequestId.get();
  }

  public String lastHost() {
    return lastHost.get();
  }

  public String lastForwardedHost() {
    return lastForwardedHost.get();
  }

  public void stop() {
    server.stop(0);
  }

  public static Path writeConfig(TestUpstream upstream, Path directory) throws IOException {
    String json =
        """
        {
          "gateway": { "listenAddress": "127.0.0.1", "port": 8080 },
          "routes": [
            {
              "id": "orders-route",
              "host": "api.autoapi.local",
              "pathPrefix": "/v1/orders",
              "methods": ["GET", "POST"],
              "upstream": { "url": "http://127.0.0.1:%d" }
            }
          ]
        }
        """
            .formatted(upstream.port());
    Path config = directory.resolve("runtime-test.json");
    Files.writeString(config, json);
    return config;
  }

  public static Path writeConfigWithUpstreamUrl(Path directory, String upstreamUrl)
      throws IOException {
    String json =
        """
        {
          "gateway": { "listenAddress": "127.0.0.1", "port": 8080 },
          "routes": [
            {
              "id": "orders-route",
              "host": "api.autoapi.local",
              "pathPrefix": "/v1/orders",
              "methods": ["GET", "POST"],
              "upstream": { "url": "%s" }
            }
          ]
        }
        """
            .formatted(upstreamUrl);
    Path config = directory.resolve("runtime-test.json");
    Files.writeString(config, json);
    return config;
  }

  public static ApplicationContextInitializer<ConfigurableApplicationContext> initializer(
      Path configPath) {
    ObjectMapper mapper = new ObjectMapper();
    RuntimeConfig config = GatewayBootstrap.loadAndValidate(configPath.toString(), mapper);
    return GatewayBootstrap.initializer(config);
  }
}
