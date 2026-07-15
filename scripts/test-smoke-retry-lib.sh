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

sample_budget='{"gatewayId":"gateway-a","budgets":[{"apiId":"'${API_A}'","routeId":"budget-route","policyId":"'${POLICY_A}'","originalRequests":2,"retriesUsed":0,"retryCapacity":2}]}'
assert_retry_budget_capacity "${sample_budget}" "${API_A}" "budget-route" "${POLICY_A}" 2 0
echo "PASS budget capacity assertion"

set +e
assert_retry_budget_capacity "${sample_budget}" "${API_A}" "budget-route" "missing" 2 0 >/dev/null 2>&1
missing_capacity_status=$?
set -e
if [[ ${missing_capacity_status} -eq 0 ]]; then
  echo "FAIL missing policy accepted for capacity assertion" >&2
  exit 1
fi
echo "PASS missing policy rejected for capacity assertion"

allowed_before='{"gatewayId":"gateway-a","budgets":[{"apiId":"'${API_A}'","routeId":"budget-route","policyId":"'${POLICY_A}'","originalRequests":1,"retriesUsed":0,"retryCapacity":5,"retryAttempts":0,"retrySuccesses":0,"windowStartedAt":"2026-01-01T00:00:00Z","windowEndsAt":"2026-01-01T00:00:06Z"}]}'
allowed_after='{"gatewayId":"gateway-a","budgets":[{"apiId":"'${API_A}'","routeId":"budget-route","policyId":"'${POLICY_A}'","originalRequests":1,"retriesUsed":1,"retryCapacity":5,"retryAttempts":1,"retrySuccesses":1,"windowStartedAt":"2026-01-01T00:00:00Z","windowEndsAt":"2026-01-01T00:00:06Z"}]}'
assert_retry_budget_allowed_retry_deltas \
  "${allowed_before}" "${allowed_after}" "${API_A}" "budget-route" "${POLICY_A}"
echo "PASS allowed retry deltas without originalRequests"

rolling_before='{"gatewayId":"gateway-a","budgets":[{"apiId":"'${API_A}'","routeId":"budget-route","policyId":"'${POLICY_A}'","originalRequests":2,"retriesUsed":1,"retryCapacity":5,"retryAttempts":1,"retrySuccesses":1,"windowStartedAt":"2026-01-01T00:00:00Z","windowEndsAt":"2026-01-01T00:00:06Z"}]}'
rolling_after='{"gatewayId":"gateway-a","budgets":[{"apiId":"'${API_A}'","routeId":"budget-route","policyId":"'${POLICY_A}'","originalRequests":2,"retriesUsed":2,"retryCapacity":5,"retryAttempts":2,"retrySuccesses":2,"windowStartedAt":"2026-01-01T00:00:01Z","windowEndsAt":"2026-01-01T00:00:07Z"}]}'
assert_retry_budget_allowed_retry_deltas \
  "${rolling_before}" "${rolling_after}" "${API_A}" "budget-route" "${POLICY_A}"
echo "PASS rolling window timestamp advance with valid counter increase"

stale_before='{"gatewayId":"gateway-a","budgets":[{"apiId":"'${API_A}'","routeId":"budget-route","policyId":"'${POLICY_A}'","originalRequests":1,"retriesUsed":1,"retryCapacity":5,"retryAttempts":1,"retrySuccesses":1,"windowStartedAt":"2026-01-01T00:00:00Z","windowEndsAt":"2026-01-01T00:00:06Z"}]}'
stale_after='{"gatewayId":"gateway-a","budgets":[{"apiId":"'${API_A}'","routeId":"budget-route","policyId":"'${POLICY_A}'","originalRequests":1,"retriesUsed":1,"retryCapacity":5,"retryAttempts":1,"retrySuccesses":1,"windowStartedAt":"2026-01-01T00:00:00Z","windowEndsAt":"2026-01-01T00:00:06Z"}]}'
set +e
assert_retry_budget_allowed_retry_deltas \
  "${stale_before}" "${stale_after}" "${API_A}" "budget-route" "${POLICY_A}" >/dev/null 2> /tmp/smoke-retry-lib-stale.err
stale_status=$?
set -e
if [[ ${stale_status} -eq 0 ]]; then
  echo "FAIL unchanged counters accepted for allowed retry" >&2
  exit 1
fi
echo "PASS unchanged counters rejected for allowed retry"

regress_before='{"gatewayId":"gateway-a","budgets":[{"apiId":"'${API_A}'","routeId":"budget-route","policyId":"'${POLICY_A}'","originalRequests":2,"retriesUsed":2,"retryCapacity":5,"retryAttempts":2,"retrySuccesses":2}]}'
regress_after='{"gatewayId":"gateway-a","budgets":[{"apiId":"'${API_A}'","routeId":"budget-route","policyId":"'${POLICY_A}'","originalRequests":2,"retriesUsed":1,"retryCapacity":5,"retryAttempts":2,"retrySuccesses":2}]}'
set +e
assert_retry_budget_allowed_retry_deltas \
  "${regress_before}" "${regress_after}" "${API_A}" "budget-route" "${POLICY_A}" >/dev/null 2> /tmp/smoke-retry-lib-regress.err
regress_status=$?
set -e
if [[ ${regress_status} -eq 0 ]]; then
  echo "FAIL counter regression accepted" >&2
  exit 1
fi
echo "PASS counter regression rejected"

capacity_before='{"gatewayId":"gateway-a","budgets":[{"apiId":"'${API_A}'","routeId":"budget-route","policyId":"'${POLICY_A}'","originalRequests":2,"retriesUsed":1,"retryCapacity":5,"retryAttempts":1,"retrySuccesses":1}]}'
capacity_after='{"gatewayId":"gateway-a","budgets":[{"apiId":"'${API_A}'","routeId":"budget-route","policyId":"'${POLICY_A}'","originalRequests":2,"retriesUsed":2,"retryCapacity":6,"retryAttempts":2,"retrySuccesses":2}]}'
set +e
assert_retry_budget_allowed_retry_deltas \
  "${capacity_before}" "${capacity_after}" "${API_A}" "budget-route" "${POLICY_A}" >/dev/null 2> /tmp/smoke-retry-lib-capacity.err
capacity_status=$?
set -e
if [[ ${capacity_status} -eq 0 ]]; then
  echo "FAIL unexpected capacity change accepted" >&2
  exit 1
fi
echo "PASS unexpected capacity change rejected"

rollover_before='{"gatewayId":"gateway-a","budgets":[{"apiId":"'${API_A}'","routeId":"budget-route","policyId":"'${POLICY_A}'","originalRequests":1,"retriesUsed":1,"retryCapacity":5,"retryAttempts":1,"retrySuccesses":1,"windowStartedAt":"2026-01-01T00:00:00Z","windowEndsAt":"2026-01-01T00:00:06Z"}]}'
rollover_after='{"gatewayId":"gateway-a","budgets":[{"apiId":"'${API_A}'","routeId":"budget-route","policyId":"'${POLICY_A}'","originalRequests":0,"retriesUsed":0,"retryCapacity":5,"retryAttempts":0,"retrySuccesses":0,"windowStartedAt":"2026-01-01T00:00:10Z","windowEndsAt":"2026-01-01T00:00:16Z"}]}'
set +e
assert_retry_budget_allowed_retry_deltas \
  "${rollover_before}" "${rollover_after}" "${API_A}" "budget-route" "${POLICY_A}" >/dev/null 2> /tmp/smoke-retry-lib-rollover.err
rollover_status=$?
set -e
if [[ ${rollover_status} -eq 0 ]]; then
  echo "FAIL destructive counter reset accepted" >&2
  exit 1
fi
echo "PASS destructive counter reset rejected"

denied_before='{"gatewayId":"gateway-a","budgets":[{"apiId":"'${API_A}'","routeId":"budget-route","policyId":"'${POLICY_A}'","originalRequests":5,"retriesUsed":5,"retryCapacity":5,"retryAttempts":5,"retrySuccesses":5,"budgetDenials":0,"windowStartedAt":"2026-01-01T00:00:00Z","windowEndsAt":"2026-01-01T00:00:06Z"}]}'
denied_after='{"gatewayId":"gateway-a","budgets":[{"apiId":"'${API_A}'","routeId":"budget-route","policyId":"'${POLICY_A}'","originalRequests":5,"retriesUsed":5,"retryCapacity":5,"retryAttempts":5,"retrySuccesses":5,"budgetDenials":1,"windowStartedAt":"2026-01-01T00:00:01Z","windowEndsAt":"2026-01-01T00:00:07Z"}]}'
assert_retry_budget_denied_retry_deltas \
  "${denied_before}" "${denied_after}" "${API_A}" "budget-route" "${POLICY_A}"
echo "PASS denied retry with rolling timestamp advance"

clean_before='{"gatewayId":"gateway-a","budgets":[{"apiId":"'${API_A}'","routeId":"budget-route","policyId":"'${POLICY_A}'","originalRequests":0,"retriesUsed":0,"retryCapacity":5,"retryAttempts":0,"retrySuccesses":0}]}'
clean_after='{"gatewayId":"gateway-a","budgets":[{"apiId":"'${API_A}'","routeId":"budget-route","policyId":"'${POLICY_A}'","originalRequests":1,"retriesUsed":0,"retryCapacity":5,"retryAttempts":0,"retrySuccesses":0}]}'
assert_budget_direct_request_no_retry \
  "${clean_before}" "${clean_after}" "${API_A}" "budget-route" "${POLICY_A}"
echo "PASS clean budget prime with no retry"

dirty_after='{"gatewayId":"gateway-a","budgets":[{"apiId":"'${API_A}'","routeId":"budget-route","policyId":"'${POLICY_A}'","originalRequests":1,"retriesUsed":1,"retryCapacity":5,"retryAttempts":1,"retrySuccesses":1}]}'
set +e
assert_budget_direct_request_no_retry \
  "${clean_before}" "${dirty_after}" "${API_A}" "budget-route" "${POLICY_A}" >/dev/null 2> /tmp/smoke-retry-lib-dirty-prime.err
dirty_prime_status=$?
set -e
if [[ ${dirty_prime_status} -eq 0 ]]; then
  echo "FAIL dirty budget prime accepted" >&2
  exit 1
fi
if ! grep -q "Budget prime unexpectedly retried" /tmp/smoke-retry-lib-dirty-prime.err; then
  echo "FAIL dirty budget prime missing retry message" >&2
  cat /tmp/smoke-retry-lib-dirty-prime.err >&2
  exit 1
fi
echo "PASS budget prime retry-counter increase rejected"

assert_retry_budget_counter_delta "retriesUsed" 0 1 1
echo "PASS counter delta before=0 after=1 expected=1"

assert_retry_budget_counter_delta "retriesUsed" 1 2 1
echo "PASS counter delta before=1 after=2 expected=1"

assert_retry_budget_counter_delta "retriesUsed" 5 5 0
echo "PASS counter delta before=5 after=5 expected=0"

set +e
assert_retry_budget_counter_delta "retriesUsed" 1 2 0 >/dev/null 2>&1
wrong_delta_status=$?
set -e
if [[ ${wrong_delta_status} -eq 0 ]]; then
  echo "FAIL counter delta before=1 after=2 expected=0 accepted" >&2
  exit 1
fi
echo "PASS counter delta before=1 after=2 expected=0 rejected"

for retry_number in 1 2 3 4 5; do
  before_used=$((retry_number - 1))
  after_used="${retry_number}"
  assert_retry_budget_counter_delta "retriesUsed" "${before_used}" "${after_used}" 1
done
echo "PASS retry numbers 1 through 5 all use delta 1"

deny_before='{"gatewayId":"gateway-a","budgets":[{"apiId":"'${API_A}'","routeId":"budget-route","policyId":"'${POLICY_A}'","originalRequests":5,"retriesUsed":5,"retryCapacity":5,"retryAttempts":5,"retrySuccesses":5,"budgetDenials":0}]}'
deny_after='{"gatewayId":"gateway-a","budgets":[{"apiId":"'${API_A}'","routeId":"budget-route","policyId":"'${POLICY_A}'","originalRequests":5,"retriesUsed":5,"retryCapacity":5,"retryAttempts":5,"retrySuccesses":5,"budgetDenials":1}]}'
delta_line="$(compute_retry_budget_deltas "${deny_before}" "${deny_after}" "${API_A}" "budget-route" "${POLICY_A}")"
IFS='|' read -r _orig_delta used_delta attempts_delta successes_delta denials_delta <<<"${delta_line}"
assert_eq "denied retriesUsed delta" "0" "${used_delta}"
assert_eq "denied budgetDenials delta" "1" "${denials_delta}"
echo "PASS denied request uses retry delta 0 and budgetDenials delta 1"

echo "All retry-status helper self-tests passed"
