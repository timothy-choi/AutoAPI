#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=scripts/smoke-phase5-parser-lib.sh
source "${ROOT}/scripts/smoke-phase5-parser-lib.sh"

TARGET_A="00000000-0000-0000-0000-000000000101"
TARGET_B="00000000-0000-0000-0000-000000000102"
TARGET_C="00000000-0000-0000-0000-000000000103"
TARGET_D="00000000-0000-0000-0000-000000000104"

sample_health() {
  cat <<EOF
{
  "gatewayId": "gateway-a",
  "pools": [
    {
      "apiId": "00000000-0000-0000-0000-000000000001",
      "poolId": "00000000-0000-0000-0000-000000000010",
      "targets": [
        {
          "targetId": "${TARGET_B}",
          "url": "http://upstream-v2:8080",
          "state": "HEALTHY",
          "consecutiveFailures": 0,
          "ejectedUntil": null,
          "lastFailureCategory": null
        },
        {
          "targetId": "${TARGET_A}",
          "url": "http://upstream-v1:8080",
          "state": "EJECTED",
          "consecutiveFailures": 0,
          "ejectedUntil": "2026-01-01T00:00:30Z",
          "lastFailureCategory": "CONNECTION_REFUSED"
        },
        {
          "targetId": "${TARGET_D}",
          "url": "http://upstream-v1:8080",
          "state": "HEALTHY",
          "consecutiveFailures": 1,
          "ejectedUntil": null,
          "lastFailureCategory": "CONNECTION_TIMEOUT"
        }
      ]
    },
    {
      "apiId": "00000000-0000-0000-0000-000000000002",
      "poolId": "00000000-0000-0000-0000-000000000020",
      "targets": [
        {
          "targetId": "${TARGET_C}",
          "url": "http://upstream-other:8080",
          "state": "HEALTHY",
          "consecutiveFailures": 0,
          "ejectedUntil": null,
          "lastFailureCategory": null
        }
      ]
    }
  ]
}
EOF
}

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

read_parsed_fields() {
  local parsed="$1"
  IFS='|' read -r state failures ejected_until category <<<"${parsed}"
}

echo "== Parser self-tests =="

parsed="$(printf '%s' "$(sample_health)" | parse_target_health "${TARGET_A}")"
read_parsed_fields "${parsed}"
assert_eq "ejected target state" "EJECTED" "${state}"
assert_eq "ejected consecutive failures" "0" "${failures}"
assert_eq "ejected until" "2026-01-01T00:00:30Z" "${ejected_until}"
assert_eq "ejected failure category" "CONNECTION_REFUSED" "${category}"

parsed="$(printf '%s' "$(sample_health)" | parse_target_health "${TARGET_B}")"
read_parsed_fields "${parsed}"
assert_eq "healthy empty state" "HEALTHY" "${state}"
assert_eq "healthy empty failures" "0" "${failures}"
assert_eq "healthy empty ejectedUntil" "" "${ejected_until}"
assert_eq "healthy empty lastFailureCategory" "" "${category}"

parsed="$(printf '%s' "$(sample_health)" | parse_target_health "${TARGET_D}")"
read_parsed_fields "${parsed}"
assert_eq "healthy with category state" "HEALTHY" "${state}"
assert_eq "healthy with category failures" "1" "${failures}"
assert_eq "healthy with category ejectedUntil" "" "${ejected_until}"
assert_eq "healthy with category lastFailureCategory" "CONNECTION_TIMEOUT" "${category}"

parsed="$(printf '%s' "$(sample_health)" | parse_target_health "${TARGET_C}")"
read_parsed_fields "${parsed}"
assert_eq "second pool target state" "HEALTHY" "${state}"
assert_eq "second pool target failures" "0" "${failures}"

assert_eq "qualifying CONNECTION_REFUSED" "0" "$(is_qualifying_stopped_upstream_transport_category CONNECTION_REFUSED && echo 0 || echo 1)"
assert_eq "qualifying CONNECTION_TIMEOUT" "0" "$(is_qualifying_stopped_upstream_transport_category CONNECTION_TIMEOUT && echo 0 || echo 1)"
assert_eq "qualifying CONNECTION_RESET" "0" "$(is_qualifying_stopped_upstream_transport_category CONNECTION_RESET && echo 0 || echo 1)"
if is_qualifying_stopped_upstream_transport_category HTTP_500; then
  echo "FAIL non-qualifying HTTP_500 accepted" >&2
  exit 1
fi
echo "PASS non-qualifying HTTP_500 rejected"
if is_qualifying_stopped_upstream_transport_category ""; then
  echo "FAIL empty category accepted" >&2
  exit 1
fi
echo "PASS empty category rejected"

set +e
printf '%s' "$(sample_health)" | parse_target_health "missing-target-id" >/dev/null
missing_status=$?
set -e
if [[ ${missing_status} -ne 2 ]]; then
  echo "FAIL target not found: expected exit 2, got ${missing_status}" >&2
  exit 1
fi
echo "PASS target not found"

set +e
printf '%s' '{"gatewayId":"gateway-a","pools":[' | parse_target_health "${TARGET_A}" >/dev/null 2> /tmp/smoke-phase5-parser-malformed.err
malformed_status=$?
set -e
if [[ ${malformed_status} -ne 1 ]]; then
  echo "FAIL malformed JSON: expected exit 1, got ${malformed_status}" >&2
  cat /tmp/smoke-phase5-parser-malformed.err >&2
  exit 1
fi
if ! grep -q "health JSON parse error" /tmp/smoke-phase5-parser-malformed.err; then
  echo "FAIL malformed JSON: missing parse error message" >&2
  cat /tmp/smoke-phase5-parser-malformed.err >&2
  exit 1
fi
rm -f /tmp/smoke-phase5-parser-malformed.err
echo "PASS malformed JSON"

wrapper_out=""
if ! wrapper_out="$(read_parsed_target_health "$(sample_health)" "${TARGET_A}")"; then
  echo "FAIL read_parsed_target_health wrapper" >&2
  exit 1
fi
read_parsed_fields "${wrapper_out}"
assert_eq "wrapper target found" "EJECTED" "${state}"

echo "All parser self-tests passed"
