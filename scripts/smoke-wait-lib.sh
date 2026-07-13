#!/usr/bin/env bash
# Bounded polling helpers for smoke scripts.

# shellcheck source=scripts/smoke-curl-lib.sh
if [[ -z "${SMOKE_CURL_LIB_LOADED:-}" ]]; then
  SMOKE_CURL_LIB_LOADED=1
  _SMOKE_WAIT_LIB_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
  # shellcheck source=scripts/smoke-curl-lib.sh
  source "${_SMOKE_WAIT_LIB_DIR}/smoke-curl-lib.sh"
fi

wait_until() {
  local description="$1"
  local attempts="$2"
  local delay_seconds="$3"
  shift 3

  local attempt
  for attempt in $(seq 1 "${attempts}"); do
    if "$@"; then
      return 0
    fi

    log_step "Waiting for ${description} (${attempt}/${attempts})"
    sleep "${delay_seconds}"
  done

  echo "Timed out waiting for ${description} after ${attempts} attempts" >&2
  return 1
}

wait_http_ready() {
  local url="$1"
  smoke_curl --fail "${url}/readyz" >/dev/null 2>&1
}

wait_container_healthy() {
  local name="$1"
  local status
  status="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "${name}" 2>/dev/null || echo none)"
  [[ "${status}" == "healthy" || "${status}" == "none" ]] \
    && [[ "$(docker inspect -f '{{.State.Running}}' "${name}" 2>/dev/null || echo false)" == "true" ]]
}
