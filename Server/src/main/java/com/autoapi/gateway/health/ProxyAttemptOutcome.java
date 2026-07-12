package com.autoapi.gateway.health;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Request-scoped guard ensuring exactly one terminal passive-health outcome is recorded per
 * upstream proxy attempt.
 */
public final class ProxyAttemptOutcome {

  private enum State {
    UNRECORDED,
    SUCCESS,
    FAILURE
  }

  private final AtomicReference<State> state = new AtomicReference<>(State.UNRECORDED);

  public boolean recordSuccess(Runnable onSuccess) {
    if (state.compareAndSet(State.UNRECORDED, State.SUCCESS)) {
      onSuccess.run();
      return true;
    }
    return false;
  }

  public boolean recordFailure(Runnable onFailure) {
    if (state.compareAndSet(State.UNRECORDED, State.FAILURE)) {
      onFailure.run();
      return true;
    }
    return false;
  }

  public boolean isRecorded() {
    return state.get() != State.UNRECORDED;
  }
}
