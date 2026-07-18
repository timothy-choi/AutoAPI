package com.autoapi.controlplane.events.webhooks;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

public final class WebhookHttpClient {

  private final WebClient webClient;
  private final WebhooksProperties properties;

  public WebhookHttpClient(WebhooksProperties properties) {
    this.properties = properties;
    HttpClient httpClient =
        HttpClient.create()
            .responseTimeout(Duration.ofMillis(60000))
            .followRedirect(properties.security().followRedirects());
    this.webClient =
        WebClient.builder()
            .clientConnector(
                new org.springframework.http.client.reactive.ReactorClientHttpConnector(httpClient))
            .build();
  }

  public Mono<DeliveryResponse> post(
      String url, java.util.Map<String, String> headers, byte[] body, Duration timeout) {
    URI uri = URI.create(url);
    validateResolvedDestination(uri);
    return webClient
        .post()
        .uri(uri)
        .headers(httpHeaders -> copyHeaders(httpHeaders, headers))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(new String(body, StandardCharsets.UTF_8))
        .exchangeToMono(response -> readResponse(response, timeout))
        .timeout(timeout);
  }

  private Mono<DeliveryResponse> readResponse(ClientResponse response, Duration timeout) {
    return response
        .bodyToMono(String.class)
        .defaultIfEmpty("")
        .map(
            body ->
                new DeliveryResponse(
                    response.statusCode().value(),
                    response.headers().asHttpHeaders().getFirst(HttpHeaders.RETRY_AFTER),
                    truncate(body, properties.maxResponsePreviewBytes())));
  }

  private void validateResolvedDestination(URI uri) {
    WebhookUrlValidator.validate(uri.toString(), properties.security());
    if (properties.security().allowPrivateAddresses()) {
      return;
    }
    try {
      InetAddress[] addresses = InetAddress.getAllByName(uri.getHost());
      for (InetAddress address : addresses) {
        if (address.isLoopbackAddress()
            || address.isLinkLocalAddress()
            || address.isSiteLocalAddress()) {
          throw new IllegalArgumentException("Blocked webhook destination address");
        }
      }
    } catch (UnknownHostException ex) {
      throw new IllegalArgumentException("Webhook host could not be resolved", ex);
    }
  }

  private static void copyHeaders(HttpHeaders target, java.util.Map<String, String> headers) {
    headers.forEach(target::set);
  }

  private static String truncate(String body, int maxBytes) {
    if (body == null) {
      return null;
    }
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    if (bytes.length <= maxBytes) {
      return body;
    }
    return new String(bytes, 0, maxBytes, StandardCharsets.UTF_8);
  }

  public record DeliveryResponse(int statusCode, String retryAfter, String bodyPreview) {}
}
