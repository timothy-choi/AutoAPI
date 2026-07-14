#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=scripts/smoke-retry-lib.sh
source "${ROOT}/scripts/smoke-retry-lib.sh"

API_A="00000000-0000-0000-0000-000000000001"
API_B="00000000-0000-0000-0000-000000000002"
ROUTE_A="route-a"
ROUTE_B="route-b"
POLICY_A="00000000-0000-0000-0000-000000000010"
POLICY_B="00000000-0000-0000-0000-000000000020"

sample_status() {
  cat <<EOF
{
  "gatewayId": "gateway-a",
  "budgets": [
    {
      "apiId": "${API_A}",
      "routeId": "${ROUTE_A}",
      "policyId": "${POLICY_A}",
      "windowSeconds": 60,
      "originalRequests": 3,
      "retriesUsed": 1,
      "retryCapacity": 10,
      "retryAttempts": 1,
      "retrySuccesses": 1,
      "retryFailures": 0,
      "budgetDenials": 0
    },
    {
      "apiId": "${API_B}",
      "routeId": "${ROUTE_B}",
      "policyId": "${POLICY_B}",
      "windowSeconds": 60,
      "originalRequests": 99,
      "retriesUsed": 99,
      "retryCapacity": 100,
      "retryAttempts": 99,
      "retrySuccesses": 99,
      "retryFailures": 99,
      "budgetDenials": 99
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

echo "== Retry-status helper self-tests =="

parsed="$(parse_retry_status_entry "$(sample_status)" "${API_A}" "${ROUTE_A}" "${POLICY_A}")"
IFS='|' read -r originals used capacity attempts successes failures denials <<<"${parsed}"
assert_eq "matching route/policy originals" "3" "${originals}"
assert_eq "matching route/policy retriesUsed" "1" "${used}"
assert_eq "matching route/policy capacity" "10" "${capacity}"

set +e
parse_retry_status_entry "$(sample_status)" "${API_A}" "wrong-route" "${POLICY_A}" >/dev/null
missing_status=$?
set -e
if [[ ${missing_status} -ne 2 ]]; then
  echo "FAIL missing entry: expected exit 2, got ${missing_status}" >&2
  exit 1
fi
echo "PASS missing entry returns nonzero"

before='{"gatewayId":"gateway-a","budgets":[{"apiId":"'${API_A}'","routeId":"'${ROUTE_A}'","policyId":"'${POLICY_A}'","originalRequests":2,"retriesUsed":0,"retryCapacity":10,"retryAttempts":0,"retrySuccesses":0}]}'
after='{"gatewayId":"gateway-a","budgets":[{"apiId":"'${API_A}'","routeId":"'${ROUTE_A}'","policyId":"'${POLICY_A}'","originalRequests":2,"retriesUsed":0,"retryCapacity":10,"retryAttempts":0,"retrySuccesses":0}]}'
set +e
assert_retry_counter_deltas "${before}" "${after}" "${API_A}" "${ROUTE_A}" "${POLICY_A}" 1 1 1 1 >/dev/null 2> /tmp/smoke-retry-lib-zero-delta.err
zero_delta_status=$?
set -e
if [[ ${zero_delta_status} -eq 0 ]]; then
  echo "FAIL zero delta accepted" >&2
  exit 1
fi
echo "PASS zero delta rejected"

after_ok='{"gatewayId":"gateway-a","budgets":[{"apiId":"'${API_A}'","routeId":"'${ROUTE_A}'","policyId":"'${POLICY_A}'","originalRequests":3,"retriesUsed":1,"retryCapacity":10,"retryAttempts":1,"retrySuccesses":1}]}'
assert_retry_counter_deltas "${before}" "${after_ok}" "${API_A}" "${ROUTE_A}" "${POLICY_A}" 1 1 1 1
echo "PASS delta one accepted"

wrong_pick='{"gatewayId":"gateway-a","budgets":[{"apiId":"'${API_B}'","routeId":"'${ROUTE_B}'","policyId":"'${POLICY_B}'","originalRequests":3,"retriesUsed":1,"retryCapacity":10,"retryAttempts":1,"retrySuccesses":1}]}'
set +e
assert_retry_counter_deltas "${before}" "${wrong_pick}" "${API_A}" "${ROUTE_A}" "${POLICY_A}" 1 1 1 1 >/dev/null 2> /tmp/smoke-retry-lib-wrong-entry.err
wrong_status=$?
set -e
if [[ ${wrong_status} -eq 0 ]]; then
  echo "FAIL wrong budget entry selected" >&2
  exit 1
fi
echo "PASS multiple entries do not select wrong route"

subshell_flag=false
echo "line" | while read -r _; do
  subshell_flag=true
done
if [[ "${subshell_flag}" == "true" ]]; then
  echo "FAIL subshell demonstration: piped while unexpectedly persisted flag" >&2
  exit 1
fi
parent_flag=false
while read -r _; do
  parent_flag=true
done < <(echo "line")
if [[ "${parent_flag}" != "true" ]]; then
  echo "FAIL process substitution while lost flag" >&2
  exit 1
fi
echo "PASS subshell flag scope behaves as expected"

echo "All retry-status helper self-tests passed"
