#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FAKE_CURL_ARGS=()

smoke_curl() {
  FAKE_CURL_ARGS=("$@")
  return 0
}

SMOKE_CURL_LIB_LOADED=1
report_curl_failure() {
  return 0
}

# shellcheck source=scripts/smoke-management-auth-lib.sh
source "${ROOT}/scripts/smoke-management-auth-lib.sh"

assert_eq() {
  local label="$1"
  local expected="$2"
  local actual="$3"
  if [[ "${expected}" != "${actual}" ]]; then
    echo "FAIL ${label}: expected '${expected}', got '${actual}'" >&2
    exit 1
  fi
  echo "PASS ${label}"
}

assert_fail() {
  local label="$1"
  shift
  if "$@"; then
    echo "FAIL ${label}: expected failure" >&2
    exit 1
  fi
  echo "PASS ${label}"
}

assert_ok() {
  local label="$1"
  shift
  if "$@"; then
    echo "PASS ${label}"
  else
    echo "FAIL ${label}" >&2
    exit 1
  fi
}

assert_ok "set_management_auth_token stores token" set_management_auth_token "test-token"
assert_eq "auth args length" "2" "${#MANAGEMENT_AUTH_ARGS[@]}"
assert_eq "auth header flag" "-H" "${MANAGEMENT_AUTH_ARGS[0]}"
assert_eq "auth header value" "Authorization: Bearer test-token" "${MANAGEMENT_AUTH_ARGS[1]}"

FAKE_CURL_ARGS=()
management_curl -X GET "http://example.com/management/projects"
assert_eq "management_curl arg count includes auth" "5" "${#FAKE_CURL_ARGS[@]}"
assert_eq "management_curl passes -H" "-H" "${FAKE_CURL_ARGS[0]}"
assert_eq "management_curl passes combined Authorization header" \
  "Authorization: Bearer test-token" "${FAKE_CURL_ARGS[1]}"
assert_eq "management_curl preserves method" "-X" "${FAKE_CURL_ARGS[2]}"
assert_eq "management_curl preserves url" "http://example.com/management/projects" "${FAKE_CURL_ARGS[4]}"

MANAGEMENT_AUTH_ARGS=()
MANAGEMENT_TOKEN=""
assert_fail "empty token rejected" set_management_auth_token ""

assert_fail "invalid bootstrap status rejected" _smoke_validate_http_status '000000{"code":"AUTHENTICATION_REQUIRED"}401'

SMOKE_HEADERS_FILE="$(mktemp)"
SMOKE_BODY_FILE="$(mktemp)"
trap 'rm -f "${SMOKE_HEADERS_FILE}" "${SMOKE_BODY_FILE}"' EXIT

set_management_auth_token "test-token"
smoke_curl() {
  FAKE_CURL_ARGS=("$@")
  return 7
}
FAKE_CURL_ARGS=()
set +e
management_curl -X GET "http://example.com/fail"
transport_exit=$?
set -e
assert_fail "transport failure returns non-zero" test "${transport_exit}" -eq 0
assert_eq "transport failure keeps auth header first" "-H" "${FAKE_CURL_ARGS[0]}"
assert_eq "transport failure keeps method arg separate" "-X" "${FAKE_CURL_ARGS[2]}"

SMOKE_BODY_FILE="$(mktemp)"
printf '{"code":"AUTHENTICATION_REQUIRED"}' >"${SMOKE_BODY_FILE}"
smoke_curl() {
  FAKE_CURL_ARGS=("$@")
  printf '401'
  return 0
}
if (control_plane_mutate "401 body separate from status" -X GET "http://example.com/unauthorized"); then
  http_check_exit=0
else
  http_check_exit=$?
fi
assert_fail "401 response fails mutate" test "${http_check_exit}" -eq 0
assert_ok "401 status validates as HTTP code" _smoke_validate_http_status "401"

bootstrap_response_file="$(mktemp)"
printf '{"notToken":"value"}' >"${bootstrap_response_file}"
if MANAGEMENT_TOKEN="$(
  python3 - "${bootstrap_response_file}" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as handle:
    document = json.load(handle)
token = document.get("token")
if not isinstance(token, str) or not token:
    raise SystemExit("bootstrap response does not contain a valid token")
print(token, end="")
PY
)"; then
  echo "FAIL malformed bootstrap response accepted" >&2
  exit 1
fi
echo "PASS malformed bootstrap response rejected"

diagnostic_output="$(
  set_management_auth_token "super-secret-token-value" >/dev/null
  echo "Management token acquired (length=${#MANAGEMENT_TOKEN})"
)"
assert_eq "diagnostics omit token value" \
  "Management token acquired (length=24)" "${diagnostic_output}"
if [[ "${diagnostic_output}" == *"super-secret"* ]]; then
  echo "FAIL diagnostics leaked token" >&2
  exit 1
fi
echo "PASS diagnostics redact token value"

capture_file="$(mktemp)"
_smoke_fake_curl_capture() {
  local output_file="" write_out=""
  while (($# > 0)); do
    case "$1" in
      --output)
        output_file="$2"
        shift 2
        ;;
      --write-out)
        write_out="$2"
        shift 2
        ;;
      *)
        shift
        ;;
    esac
  done
  printf '{"code":"INVALID_CREDENTIAL","message":"Invalid credential"}' >"${output_file}"
  printf '401'
  return 0
}
smoke_curl() {
  _smoke_fake_curl_capture "$@"
}
if http_request_capture "${capture_file}" -H "Authorization: Bearer ak_live_test" "http://example.com/management/auth/me"; then
  capture_exit=0
else
  capture_exit=$?
fi
assert_ok "expected 401 capture succeeds at transport layer" test "${capture_exit}" -eq 0
assert_eq "capture keeps status separate" "401" "${HTTP_STATUS}"
assert_eq "capture keeps body separate" '{"code":"INVALID_CREDENTIAL","message":"Invalid credential"}' "${HTTP_BODY}"
assert_ok "expected 401 does not fail capture validation" expect_http_status "${HTTP_STATUS}" 401
assert_fail "unexpected 200 fails negative assertion" expect_http_status "200" 401
assert_ok "403 allowed-status helper accepts 403" expect_http_status "403" 401 403
assert_fail "invalid status text fails validation" _smoke_validate_http_status '401{"code":"x"}'

smoke_curl() {
  _smoke_fake_curl_capture "$@"
}
assert_ok "smoke_assert_http_rejection accepts expected 401" \
  smoke_assert_http_rejection "api key rejection" 401 INVALID_CREDENTIAL \
  -H "Authorization: Bearer ak_live_test" "http://example.com/management/auth/me"

_smoke_fake_curl_success() {
  local output_file=""
  while (($# > 0)); do
    case "$1" in
      --output)
        output_file="$2"
        shift 2
        ;;
      --write-out)
        shift 2
        ;;
      *)
        shift
        ;;
    esac
  done
  printf '{"principalType":"SERVICE_ACCOUNT"}' >"${output_file}"
  printf '200'
  return 0
}
smoke_curl() {
  _smoke_fake_curl_success "$@"
}
if smoke_assert_http_rejection "unexpected success" 401 INVALID_CREDENTIAL \
  -H "Authorization: Bearer ak_live_test" "http://example.com/management/auth/me"; then
  echo "FAIL unexpected 200 accepted by rejection helper" >&2
  exit 1
fi
echo "PASS unexpected 200 fails rejection helper"

echo "All smoke-management-auth-lib tests passed"
