#!/usr/bin/env bash
# Shared management-plane authentication helpers for smoke scripts.
# Never echo MANAGEMENT_TOKEN or bootstrap secrets to stdout.

SMOKE_BOOTSTRAP_TOKEN="${AUTOAPI_BOOTSTRAP_ADMIN_TOKEN:-smoke-bootstrap-token-change-me-for-local-dev-only}"
MANAGEMENT_TOKEN=""
MANAGEMENT_AUTH_ARGS=()

_smoke_management_require_curl_lib() {
  if [[ -z "${SMOKE_CURL_LIB_LOADED:-}" ]]; then
    local lib_dir
    lib_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
    # shellcheck source=scripts/smoke-curl-lib.sh
    source "${lib_dir}/smoke-curl-lib.sh"
  fi
}

set_management_auth_token() {
  local token="$1"

  if [[ -z "${token}" ]]; then
    echo "Management token is empty" >&2
    return 1
  fi

  MANAGEMENT_TOKEN="${token}"
  MANAGEMENT_AUTH_ARGS=(
    -H
    "Authorization: Bearer ${MANAGEMENT_TOKEN}"
  )
}

_smoke_management_resolve_auth_args() {
  if ((${#MANAGEMENT_AUTH_ARGS[@]} > 0)); then
    return 0
  fi

  if [[ -n "${MANAGEMENT_TOKEN}" ]]; then
    set_management_auth_token "${MANAGEMENT_TOKEN}"
    return 0
  fi

  MANAGEMENT_AUTH_ARGS=(
    -H
    "Authorization: Bearer ${SMOKE_BOOTSTRAP_TOKEN}"
  )
}

management_curl() {
  _smoke_management_require_curl_lib
  _smoke_management_resolve_auth_args
  smoke_curl "${MANAGEMENT_AUTH_ARGS[@]}" "$@"
}

smoke_management_auth_header() {
  _smoke_management_resolve_auth_args
  if ((${#MANAGEMENT_AUTH_ARGS[@]} >= 2)); then
    printf '%s' "${MANAGEMENT_AUTH_ARGS[1]}"
  fi
}

_smoke_validate_http_status() {
  local http_status="$1"

  if [[ ! "${http_status}" =~ ^[0-9]{3}$ ]]; then
    echo "Invalid HTTP status from curl: ${http_status}" >&2
    return 1
  fi

  return 0
}

control_plane_mutate() {
  local context="$1"
  shift
  local status curl_exit

  if [[ -z "${SMOKE_HEADERS_FILE:-}" || -z "${SMOKE_BODY_FILE:-}" ]]; then
    echo "SMOKE_HEADERS_FILE and SMOKE_BODY_FILE must be set before control_plane_mutate" >&2
    exit 1
  fi

  set +e
  { set +x; } 2>/dev/null
  status="$(
    management_curl \
      -D "${SMOKE_HEADERS_FILE}" \
      -o "${SMOKE_BODY_FILE}" \
      -w '%{http_code}' \
      "$@"
  )"
  curl_exit=$?
  set -e

  if ((curl_exit != 0)); then
    report_curl_failure "${context}" "${curl_exit}" "${status:-}"
    exit "${curl_exit}"
  fi

  if ! _smoke_validate_http_status "${status}"; then
    report_curl_failure "${context}" "${curl_exit}" "${status}"
    exit 1
  fi

  if ((status < 200 || status >= 300)); then
    report_curl_failure "${context}" "${curl_exit}" "${status}"
    if [[ -s "${SMOKE_BODY_FILE}" ]]; then
      smoke_redact_token "$(cat "${SMOKE_BODY_FILE}")" >&2 || cat "${SMOKE_BODY_FILE}" >&2
    fi
    exit 1
  fi
}

control_plane_json() {
  local context="$1"
  shift
  control_plane_mutate "${context}" "$@"
  cat "${SMOKE_BODY_FILE}"
}

smoke_bootstrap_management() {
  local control_plane_url="$1"
  local response_file="${SMOKE_BODY_FILE:-${SMOKE_BOOTSTRAP_RESPONSE_FILE:-/tmp/autoapi-smoke-bootstrap.json}}"
  local status curl_exit

  _smoke_management_require_curl_lib
  MANAGEMENT_AUTH_ARGS=(
    -H
    "Authorization: Bearer ${SMOKE_BOOTSTRAP_TOKEN}"
  )

  set +e
  { set +x; } 2>/dev/null
  status="$(
    smoke_curl \
      -o "${response_file}" \
      -w '%{http_code}' \
      -X POST "${control_plane_url}/api/v1/management/bootstrap" \
      -H 'Content-Type: application/json' \
      "${MANAGEMENT_AUTH_ARGS[@]}"
  )"
  curl_exit=$?
  set -e

  if ((curl_exit != 0)); then
    echo "Bootstrap transport failure: curl exit=${curl_exit}" >&2
    exit "${curl_exit}"
  fi

  if ! _smoke_validate_http_status "${status}"; then
    echo "Bootstrap failed with invalid HTTP status: ${status}" >&2
    exit 1
  fi

  if [[ "${status}" == "200" ]]; then
    MANAGEMENT_TOKEN="$(
      python3 - "${response_file}" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as handle:
    document = json.load(handle)
token = document.get("token")
if not isinstance(token, str) or not token:
    raise SystemExit("bootstrap response does not contain a valid token")
print(token, end="")
PY
    )"
    if [[ -z "${MANAGEMENT_TOKEN}" ]]; then
      echo "Bootstrap succeeded but returned no token" >&2
      exit 1
    fi
    set_management_auth_token "${MANAGEMENT_TOKEN}"
    echo "Management token acquired (length=${#MANAGEMENT_TOKEN})" >&2
    return 0
  fi

  if [[ "${status}" == "409" ]]; then
    MANAGEMENT_AUTH_ARGS=(
      -H
      "Authorization: Bearer ${SMOKE_BOOTSTRAP_TOKEN}"
    )
    return 0
  fi

  echo "Bootstrap failed with HTTP ${status}" >&2
  if [[ -s "${response_file}" ]]; then
    smoke_redact_token "$(cat "${response_file}")" >&2 || true
  fi
  exit 1
}

smoke_redact_token() {
  local text="$1"
  python3 - "$text" "${MANAGEMENT_TOKEN}" "${SMOKE_BOOTSTRAP_TOKEN}" <<'PY'
import sys
text, token, bootstrap = sys.argv[1], sys.argv[2], sys.argv[3]
for secret in (token, bootstrap):
    if secret:
        text = text.replace(secret, "[REDACTED]")
print(text)
PY
}
