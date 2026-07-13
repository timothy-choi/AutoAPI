#!/usr/bin/env bash
# Shared curl helpers with strict deadlines for smoke scripts.

SMOKE_CONNECT_TIMEOUT_SECONDS="${SMOKE_CONNECT_TIMEOUT_SECONDS:-3}"
SMOKE_REQUEST_TIMEOUT_SECONDS="${SMOKE_REQUEST_TIMEOUT_SECONDS:-15}"
SMOKE_CURRENT_STEP="${SMOKE_CURRENT_STEP:-unknown}"

log_step() {
  printf '[%s] %s\n' "$(date -u +'%Y-%m-%dT%H:%M:%SZ')" "$*" >&2
}

set_smoke_step() {
  SMOKE_CURRENT_STEP="$1"
  log_step "${SMOKE_CURRENT_STEP}"
}

smoke_curl() {
  curl \
    --connect-timeout "${SMOKE_CONNECT_TIMEOUT_SECONDS}" \
    --max-time "${SMOKE_REQUEST_TIMEOUT_SECONDS}" \
    --silent \
    --show-error \
    "$@"
}

report_curl_failure() {
  local context="$1"
  local curl_exit="$2"
  local status="${3:-}"

  log_step "curl failed during ${context} (exit=${curl_exit}, http=${status:-n/a}, step=${SMOKE_CURRENT_STEP})"

  if [[ "${curl_exit}" -eq 28 ]]; then
    echo "curl timed out during ${context} at step: ${SMOKE_CURRENT_STEP}" >&2
  fi
}
