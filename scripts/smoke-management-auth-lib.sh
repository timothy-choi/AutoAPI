#!/usr/bin/env bash
# Shared management-plane authentication helpers for smoke scripts.
# Never echo MANAGEMENT_TOKEN or bootstrap secrets to stdout.

SMOKE_BOOTSTRAP_TOKEN="${AUTOAPI_BOOTSTRAP_ADMIN_TOKEN:-smoke-bootstrap-token-change-me-for-local-dev-only}"
MANAGEMENT_TOKEN=""

smoke_management_auth_header() {
  if [[ -n "${MANAGEMENT_TOKEN}" ]]; then
    printf 'Authorization: Bearer %s' "${MANAGEMENT_TOKEN}"
    return 0
  fi
  printf 'Authorization: Bearer %s' "${SMOKE_BOOTSTRAP_TOKEN}"
}

smoke_bootstrap_management() {
  local control_plane_url="$1"
  local response_file="${SMOKE_BODY_FILE:-/tmp/autoapi-smoke-bootstrap.json}"
  local status
  status="$(
    smoke_curl \
      -o "${response_file}" \
      -w '%{http_code}' \
      -X POST "${control_plane_url}/api/v1/management/bootstrap" \
      -H "Content-Type: application/json" \
      -H "$(smoke_management_auth_header)"
  )"
  if [[ "${status}" == "200" ]]; then
    MANAGEMENT_TOKEN="$(
      python3 - "${response_file}" <<'PY'
import json, sys
with open(sys.argv[1], encoding="utf-8") as handle:
    payload = json.load(handle)
print(payload.get("token", ""))
PY
    )"
    if [[ -z "${MANAGEMENT_TOKEN}" ]]; then
      echo "Bootstrap succeeded but returned no token" >&2
      exit 1
    fi
    return 0
  fi
  if [[ "${status}" == "409" ]]; then
    # Already initialized; bootstrap token remains valid for smoke operations.
    return 0
  fi
  echo "Bootstrap failed with HTTP ${status}" >&2
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
