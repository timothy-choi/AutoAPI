package com.autoapi.support;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

/** Mock upstream HTTP server that can be stopped and restarted to simulate transport failures. */
public final class ControllableTestUpstream {

  private HttpServer server;
  private final int port;
  private volatile boolean accepting;
  private volatile boolean hangResponses;
  private volatile int statusCode = 200;
  private final AtomicReference<String> lastPath = new AtomicReference<>("");

  private ControllableTestUpstream(HttpServer server, int port) {
    this.server = server;
    this.port = port;
    this.accepting = true;
  }

  public static ControllableTestUpstream start() throws IOException {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    ControllableTestUpstream upstream =
        new ControllableTestUpstream(server, server.getAddress().getPort());
    upstream.attachHandler(server);
    server.start();
    return upstream;
  }

  public int port() {
    return port;
  }

  public String url() {
    return "http://127.0.0.1:" + port;
  }

  public String lastPath() {
    return lastPath.get();
  }

  public void stopAccepting() {
    if (!accepting) {
      return;
    }
    server.stop(0);
    accepting = false;
  }

  public void resumeAccepting() throws IOException {
    if (accepting) {
      return;
    }
    HttpServer replacement = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
    attachHandler(replacement);
    replacement.start();
    server = replacement;
    accepting = true;
    statusCode = 200;
    hangResponses = false;
  }

  /** Accepts TCP connections but never completes the HTTP response (client-side timeout). */
  public void hangOnRequests() {
    hangResponses = true;
  }

  public void resumeResponses() {
    hangResponses = false;
  }

  public void respondWithStatus(int statusCode) {
    this.statusCode = statusCode;
  }

  public void shutdown() {
    if (accepting) {
      server.stop(0);
      accepting = false;
    }
  }

  private void attachHandler(HttpServer httpServer) {
    httpServer.createContext(
        "/",
        exchange -> {
          lastPath.set(exchange.getRequestURI().getPath());
          if (hangResponses) {
            return;
          }
          byte[] body =
              ("{\"path\":\"" + exchange.getRequestURI().getPath() + "\"}")
                  .getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(statusCode, body.length);
          try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
          }
        });
  }
}
