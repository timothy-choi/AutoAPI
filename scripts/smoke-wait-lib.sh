#!/usr/bin/env bash
# Bounded polling helpers for smoke scripts.

wait_until() {
  local description="$1"
  local attempts="$2"
  local delay="$3"
  shift 3

  local attempt
  for attempt in $(seq 1 "${attempts}"); do
    if "$@"; then
      return 0
    fi
    sleep "${delay}"
  done

  echo "Timed out waiting for ${description} after ${attempts} attempts" >&2
  return 1
}

wait_http_ready() {
  local url="$1"
  curl --fail --silent "${url}/readyz" >/dev/null 2>&1
}

wait_container_healthy() {
  local name="$1"
  local status
  status="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "${name}" 2>/dev/null || echo none)"
  [[ "${status}" == "healthy" || "${status}" == "none" ]] \
    && [[ "$(docker inspect -f '{{.State.Running}}' "${name}" 2>/dev/null || echo false)" == "true" ]]
}
