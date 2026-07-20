#!/usr/bin/env bash
# Shared curl helpers with strict deadlines for smoke scripts.

SMOKE_CONNECT_TIMEOUT_SECONDS="${SMOKE_CONNECT_TIMEOUT_SECONDS:-3}"
SMOKE_REQUEST_TIMEOUT_SECONDS="${SMOKE_REQUEST_TIMEOUT_SECONDS:-15}"
SMOKE_CURRENT_STEP="${SMOKE_CURRENT_STEP:-unknown}"
CURRENT_STEP="${CURRENT_STEP:-initialization}"

log_step() {
  if [[ -n "${SMOKE_STARTED_AT:-}" ]]; then
    local now elapsed
    now="$(date +%s)"
    elapsed=$((now - SMOKE_STARTED_AT))
    printf '[%s elapsed=%ss] %s\n' \
      "$(date -u +'%Y-%m-%dT%H:%M:%SZ')" \
      "${elapsed}" \
      "$*" >&2
  else
    printf '[%s] %s\n' "$(date -u +'%Y-%m-%dT%H:%M:%SZ')" "$*" >&2
  fi
}

set_smoke_step() {
  CURRENT_STEP="$1"
  SMOKE_CURRENT_STEP="$1"
  log_step "${CURRENT_STEP}"
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

  log_step "curl transport failure during ${context} (curl_exit=${curl_exit}, step=${SMOKE_CURRENT_STEP})"

  if [[ "${curl_exit}" -eq 28 ]]; then
    echo "curl timed out during ${context} at step: ${SMOKE_CURRENT_STEP}" >&2
  fi
}

report_unexpected_http_status() {
  local context="$1"
  local expected="$2"
  local actual="$3"
  local curl_exit="${4:-0}"

  echo "Unexpected HTTP status during ${context}: expected=${expected} actual=${actual} curl_exit=${curl_exit}" >&2
}
