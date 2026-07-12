#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=scripts/smoke-phase5-parser-lib.sh
source "${ROOT}/scripts/smoke-phase5-parser-lib.sh"

TARGET_A="00000000-0000-0000-0000-000000000101"
TARGET_B="00000000-0000-0000-0000-000000000102"
TARGET_C="00000000-0000-0000-0000-000000000103"

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

echo "== Parser self-tests =="

parsed="$(printf '%s' "$(sample_health)" | parse_target_health "${TARGET_A}")"
IFS=$'\t' read -r state failures ejected_until category <<<"${parsed}"
assert_eq "target found" "EJECTED" "${state}"
assert_eq "consecutive failures" "0" "${failures}"
assert_eq "ejected until" "2026-01-01T00:00:30Z" "${ejected_until}"
assert_eq "failure category" "CONNECTION_REFUSED" "${category}"

parsed="$(printf '%s' "$(sample_health)" | parse_target_health "${TARGET_B}")"
IFS=$'\t' read -r state failures ejected_until category <<<"${parsed}"
assert_eq "null ejectedUntil" "" "${ejected_until}"
assert_eq "null lastFailureCategory" "" "${category}"

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
IFS=$'\t' read -r state _ _ _ <<<"${wrapper_out}"
assert_eq "wrapper target found" "EJECTED" "${state}"

echo "All parser self-tests passed"
