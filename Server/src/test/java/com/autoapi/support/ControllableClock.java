package com.autoapi.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/** Test clock with a fixed start instant that can be advanced without sleeping. */
public final class ControllableClock extends Clock {

  private volatile Instant instant;
  private final ZoneId zone;

  public ControllableClock(Instant instant, ZoneId zone) {
    this.instant = instant;
    this.zone = zone;
  }

  public static ControllableClock fixed(Instant instant) {
    return new ControllableClock(instant, ZoneId.of("UTC"));
  }

  public void advance(Duration duration) {
    instant = instant.plus(duration);
  }

  public void setInstant(Instant instant) {
    this.instant = instant;
  }

  @Override
  public ZoneId getZone() {
    return zone;
  }

  @Override
  public Clock withZone(ZoneId zone) {
    return new ControllableClock(instant, zone);
  }

  @Override
  public Instant instant() {
    return instant;
  }
}
