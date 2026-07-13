package com.autoapi.gateway.retry;

import java.util.Arrays;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.server.reactive.ServerHttpRequest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Bounded request-body capture for replayable retry attempts. */
public final class ReplayableRequest {

  public enum BodyState {
    EMPTY,
    CAPTURED,
    TOO_LARGE
  }

  private final byte[] body;
  private final BodyState state;

  private ReplayableRequest(byte[] body, BodyState state) {
    this.body = body == null ? new byte[0] : body;
    this.state = state;
  }

  public static Mono<ReplayableRequest> capture(ServerHttpRequest request, long maxBytes) {
    long contentLength = request.getHeaders().getContentLength();
    if (contentLength > maxBytes) {
      return Mono.just(new ReplayableRequest(new byte[0], BodyState.TOO_LARGE));
    }
    return org.springframework.core.io.buffer.DataBufferUtils.join(request.getBody())
        .map(
            buffer -> {
              try {
                int readable = buffer.readableByteCount();
                if (readable > maxBytes) {
                  return new ReplayableRequest(new byte[0], BodyState.TOO_LARGE);
                }
                byte[] bytes = new byte[readable];
                buffer.read(bytes);
                return new ReplayableRequest(bytes, BodyState.CAPTURED);
              } finally {
                org.springframework.core.io.buffer.DataBufferUtils.release(buffer);
              }
            })
        .defaultIfEmpty(new ReplayableRequest(new byte[0], BodyState.EMPTY));
  }

  public BodyState state() {
    return state;
  }

  public boolean replayable() {
    return state != BodyState.TOO_LARGE;
  }

  public byte[] body() {
    return body;
  }

  public Flux<DataBuffer> toBodyFlux(org.springframework.core.io.buffer.DataBufferFactory factory) {
    if (body.length == 0) {
      return Flux.empty();
    }
    return Flux.just(factory.wrap(Arrays.copyOf(body, body.length)));
  }
}
