package com.autoapi.gateway.observability;

import org.springframework.http.HttpStatusCode;

/** Bounded HTTP status classification for metrics labels. */
public enum HttpStatusClass {
  UNKNOWN,
  XX2,
  XX3,
  XX4,
  XX5,
  TRANSPORT_ERROR;

  public String label() {
    return switch (this) {
      case XX2 -> "2xx";
      case XX3 -> "3xx";
      case XX4 -> "4xx";
      case XX5 -> "5xx";
      case TRANSPORT_ERROR -> "transport_error";
      case UNKNOWN -> "unknown";
    };
  }

  public static HttpStatusClass fromStatusCode(int statusCode) {
    if (statusCode <= 0) {
      return UNKNOWN;
    }
    if (statusCode >= 200 && statusCode < 300) {
      return XX2;
    }
    if (statusCode >= 300 && statusCode < 400) {
      return XX3;
    }
    if (statusCode >= 400 && statusCode < 500) {
      return XX4;
    }
    if (statusCode >= 500) {
      return XX5;
    }
    return UNKNOWN;
  }

  public static HttpStatusClass fromStatusCode(HttpStatusCode statusCode) {
    if (statusCode == null) {
      return UNKNOWN;
    }
    return fromStatusCode(statusCode.value());
  }
}
