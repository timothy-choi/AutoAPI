#!/usr/bin/env bash
# Retry-status and snapshot assertions for Phase 6 smoke scripts.

# shellcheck source=scripts/smoke-curl-lib.sh
if [[ -z "${SMOKE_CURL_LIB_LOADED:-}" ]]; then
  SMOKE_CURL_LIB_LOADED=1
  _SMOKE_RETRY_LIB_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
  # shellcheck source=scripts/smoke-curl-lib.sh
  source "${_SMOKE_RETRY_LIB_DIR}/smoke-curl-lib.sh"
fi

assert_nonblank_gateway_id() {
  local retry_json="$1"
  python3 - "${retry_json}" <<'PY'
import json
import sys

payload = json.loads(sys.argv[1])
gateway_id = payload.get("gatewayId")
if gateway_id is None or str(gateway_id).strip() == "" or gateway_id == "unknown":
    raise SystemExit(f"gatewayId must be configured and nonblank, got {gateway_id!r}")
PY
}

assert_retry_budget_entry() {
  local retry_json="$1"
  local api_id="$2"
  local route_id="$3"
  local policy_id="$4"
  python3 - "${retry_json}" "${api_id}" "${route_id}" "${policy_id}" <<'PY'
import json
import sys

payload = json.loads(sys.argv[1])
api_id, route_id, policy_id = sys.argv[2:5]
budgets = payload.get("budgets") or []
for entry in budgets:
    if (
        entry.get("apiId") == api_id
        and entry.get("routeId") == route_id
        and entry.get("policyId") == policy_id
    ):
        originals = int(entry.get("originalRequests") or 0)
        used = int(entry.get("retriesUsed") or 0)
        capacity = int(entry.get("retryCapacity") or 0)
        if originals >= 1 and capacity > used:
            raise SystemExit(0)
        raise SystemExit(
            f"retry budget entry found but unavailable: originals={originals} used={used} capacity={capacity}"
        )
raise SystemExit("retry budget entry not found for active route/policy")
PY
}

extract_retry_budget_counters() {
  local retry_json="$1"
  local api_id="$2"
  local route_id="$3"
  local policy_id="$4"
  python3 - "${retry_json}" "${api_id}" "${route_id}" "${policy_id}" <<'PY'
import json
import sys

payload = json.loads(sys.argv[1])
api_id, route_id, policy_id = sys.argv[2:5]
for entry in payload.get("budgets") or []:
    if (
        entry.get("apiId") == api_id
        and entry.get("routeId") == route_id
        and entry.get("policyId") == policy_id
    ):
        fields = [
            int(entry.get("originalRequests") or 0),
            int(entry.get("retriesUsed") or 0),
            int(entry.get("retryCapacity") or 0),
            int(entry.get("retryAttempts") or 0),
            int(entry.get("retrySuccesses") or 0),
            int(entry.get("retryFailures") or 0),
            int(entry.get("budgetDenials") or 0),
        ]
        print("|".join(str(value) for value in fields))
        raise SystemExit(0)
raise SystemExit(2)
PY
}

parse_retry_status_entry() {
  local json="$1"
  local api_id="$2"
  local route_id="$3"
  local policy_id="$4"
  extract_retry_budget_counters "${json}" "${api_id}" "${route_id}" "${policy_id}"
}

read_retry_budget_counters() {
  local retry_json="$1"
  local api_id="$2"
  local route_id="$3"
  local policy_id="$4"
  local parsed=""
  local parse_status=0
  set +e
  parsed="$(parse_retry_status_entry "${retry_json}" "${api_id}" "${route_id}" "${policy_id}")"
  parse_status=$?
  set -e
  if [[ ${parse_status} -eq 2 ]]; then
    echo "retry budget entry not found for route=${route_id} policy=${policy_id}" >&2
    printf '%s\n' "${retry_json}" >&2
    return 1
  fi
  if [[ ${parse_status} -ne 0 ]]; then
    echo "Failed to parse retry budget counters" >&2
    printf '%s\n' "${retry_json}" >&2
    return 1
  fi
  IFS='|' read -r \
    original_requests \
    retries_used \
    retry_capacity \
    retry_attempts \
    retry_successes \
    retry_failures \
    budget_denials \
    <<<"${parsed}"
  printf '%s\n' \
    "${original_requests}" \
    "${retries_used}" \
    "${retry_capacity}" \
    "${retry_attempts}" \
    "${retry_successes}" \
    "${retry_failures}" \
    "${budget_denials}"
}

read_retry_budget_counters_or_zero() {
  local retry_json="$1"
  local api_id="$2"
  local route_id="$3"
  local policy_id="$4"
  local parsed=""
  local parse_status=0

  set +e
  parsed="$(parse_retry_status_entry "${retry_json}" "${api_id}" "${route_id}" "${policy_id}")"
  parse_status=$?
  set -e
  if [[ ${parse_status} -eq 2 ]]; then
    printf '%s\n' 0 0 0 0 0 0 0
    return 0
  fi
  if [[ ${parse_status} -ne 0 ]]; then
    echo "Failed to parse retry budget counters" >&2
    printf '%s\n' "${retry_json}" >&2
    return 1
  fi
  IFS='|' read -r \
    original_requests \
    retries_used \
    retry_capacity \
    retry_attempts \
    retry_successes \
    retry_failures \
    budget_denials \
    <<<"${parsed}"
  printf '%s\n' \
    "${original_requests}" \
    "${retries_used}" \
    "${retry_capacity}" \
    "${retry_attempts}" \
    "${retry_successes}" \
    "${retry_failures}" \
    "${budget_denials}"
}

assert_budget_direct_request_no_retry() {
  local before="$1"
  local after="$2"
  local api_id="$3"
  local route_id="$4"
  local policy_id="$5"
  python3 - "${before}" "${after}" \
    "${api_id}" "${route_id}" "${policy_id}" <<'PY'
import json
import sys

before = json.loads(sys.argv[1])
after = json.loads(sys.argv[2])
api_id, route_id, policy_id = sys.argv[3:6]

def counters(payload):
    for entry in payload.get("budgets") or []:
        if (
            entry.get("apiId") == api_id
            and entry.get("routeId") == route_id
            and entry.get("policyId") == policy_id
        ):
            return {
                "originalRequests": int(entry.get("originalRequests") or 0),
                "retriesUsed": int(entry.get("retriesUsed") or 0),
                "retryCapacity": int(entry.get("retryCapacity") or 0),
                "retryAttempts": int(entry.get("retryAttempts") or 0),
                "retrySuccesses": int(entry.get("retrySuccesses") or 0),
            }
    return {
        "originalRequests": 0,
        "retriesUsed": 0,
        "retryCapacity": 0,
        "retryAttempts": 0,
        "retrySuccesses": 0,
    }

before_counts = counters(before)
after_counts = counters(after)
checks = [
    ("originalRequests", 1),
    ("retriesUsed", 0),
    ("retryAttempts", 0),
    ("retrySuccesses", 0),
]
for field, expected_delta in checks:
    actual_delta = after_counts[field] - before_counts[field]
    if actual_delta != expected_delta:
        if field != "originalRequests" and actual_delta > 0:
            raise SystemExit(
                "Budget prime unexpectedly retried; upstream readiness or selector setup is invalid "
                f"({field} delta expected {expected_delta}, got {actual_delta}; "
                f"before={before_counts[field]} after={after_counts[field]})"
            )
        raise SystemExit(
            f"{field} delta expected {expected_delta}, got {actual_delta} "
            f"(before={before_counts[field]} after={after_counts[field]})"
        )
PY
}

print_budget_setup_failure_diagnostics() {
  local api_id="$1"
  local route_id="$2"
  local policy_id="$3"
  local retry_before="$4"
  local retry_after="$5"
  local http_status="$6"
  local service="$7"
  local health_json="${8:-}"

  echo "Budget setup failure diagnostics:" >&2
  echo "  httpStatus=${http_status}" >&2
  echo "  service=${service}" >&2
  log_retry_status_entry "before" "${retry_before}" "${api_id}" "${route_id}" "${policy_id}" >&2 || true
  log_retry_status_entry "after" "${retry_after}" "${api_id}" "${route_id}" "${policy_id}" >&2 || true
  if [[ -n "${health_json}" ]]; then
    echo "  upstream-health:" >&2
    printf '%s\n' "${health_json}" >&2
  fi
  echo "  matching attempt logs:" >&2
  docker compose logs gateway-a --no-color --tail=120 2>/dev/null \
    | grep -E 'phase6-budget-(init|position|prime)' >&2 || true
}

log_retry_status_entry() {
  local label="$1"
  local retry_json="$2"
  local api_id="$3"
  local route_id="$4"
  local policy_id="$5"
  local counters=""
  if ! counters="$(read_retry_budget_counters "${retry_json}" "${api_id}" "${route_id}" "${policy_id}")"; then
    echo "${label}: failed to read retry-status entry" >&2
    return 1
  fi
  local original_requests retries_used retry_capacity retry_attempts retry_successes retry_failures budget_denials
  original_requests="$(sed -n '1p' <<<"${counters}")"
  retries_used="$(sed -n '2p' <<<"${counters}")"
  retry_capacity="$(sed -n '3p' <<<"${counters}")"
  retry_attempts="$(sed -n '4p' <<<"${counters}")"
  retry_successes="$(sed -n '5p' <<<"${counters}")"
  retry_failures="$(sed -n '6p' <<<"${counters}")"
  budget_denials="$(sed -n '7p' <<<"${counters}")"
  log_step "${label}: originalRequests=${original_requests} retriesUsed=${retries_used} retryCapacity=${retry_capacity} retryAttempts=${retry_attempts} retrySuccesses=${retry_successes}"
}

assert_retry_failover_budget_proof() {
  local before="$1"
  local after="$2"
  local api_id="$3"
  local route_id="$4"
  local policy_id="$5"
  assert_retry_counter_deltas \
    "${before}" \
    "${after}" \
    "${api_id}" \
    "${route_id}" \
    "${policy_id}" \
    1 1 1 1
}

assert_retry_budget_capacity() {
  local retry_json="$1"
  local api_id="$2"
  local route_id="$3"
  local policy_id="$4"
  local expected_capacity="$5"
  local expected_used="$6"
  python3 - "${retry_json}" "${api_id}" "${route_id}" "${policy_id}" \
    "${expected_capacity}" "${expected_used}" <<'PY'
import json
import sys

payload = json.loads(sys.argv[1])
api_id, route_id, policy_id = sys.argv[2:5]
expected_capacity = int(sys.argv[5])
expected_used = int(sys.argv[6])
for entry in payload.get("budgets") or []:
    if (
        entry.get("apiId") == api_id
        and entry.get("routeId") == route_id
        and entry.get("policyId") == policy_id
    ):
        capacity = int(entry.get("retryCapacity") or 0)
        used = int(entry.get("retriesUsed") or 0)
        if capacity == expected_capacity and used == expected_used:
            raise SystemExit(0)
        raise SystemExit(
            f"retry budget capacity mismatch: expected capacity={expected_capacity} used={expected_used}, "
            f"got capacity={capacity} used={used}"
        )
print(json.dumps(payload, indent=2), file=sys.stderr)
raise SystemExit(f"retry budget entry not found for route={route_id} policy={policy_id}")
PY
}

# Assert one budget counter changed by expected_delta between absolute values.
# Args: field_name before_value after_value expected_delta
assert_retry_budget_counter_delta() {
  local field="$1"
  local before_value="$2"
  local after_value="$3"
  local expected_delta="$4"
  local actual_delta=$((after_value - before_value))

  if [[ "${actual_delta}" -ne "${expected_delta}" ]]; then
    echo "${field} delta expected ${expected_delta}, got ${actual_delta} (before=${before_value} after=${after_value})" >&2
    return 1
  fi
  return 0
}

# Print counter deltas as:
# originalRequests|retriesUsed|retryAttempts|retrySuccesses|budgetDenials
compute_retry_budget_deltas() {
  local before="$1"
  local after="$2"
  local api_id="$3"
  local route_id="$4"
  local policy_id="$5"
  python3 - "${before}" "${after}" \
    "${api_id}" "${route_id}" "${policy_id}" <<'PY'
import json
import sys

before = json.loads(sys.argv[1])
after = json.loads(sys.argv[2])
api_id, route_id, policy_id = sys.argv[3:6]

def counters(payload):
    for entry in payload.get("budgets") or []:
        if (
            entry.get("apiId") == api_id
            and entry.get("routeId") == route_id
            and entry.get("policyId") == policy_id
        ):
            return {
                "originalRequests": int(entry.get("originalRequests") or 0),
                "retriesUsed": int(entry.get("retriesUsed") or 0),
                "retryAttempts": int(entry.get("retryAttempts") or 0),
                "retrySuccesses": int(entry.get("retrySuccesses") or 0),
                "budgetDenials": int(entry.get("budgetDenials") or 0),
            }
    raise SystemExit(f"retry budget entry not found for route={route_id} policy={policy_id}")

before_counts = counters(before)
after_counts = counters(after)
fields = (
    "originalRequests",
    "retriesUsed",
    "retryAttempts",
    "retrySuccesses",
    "budgetDenials",
)
print("|".join(str(after_counts[field] - before_counts[field]) for field in fields))
PY
}

# Allowed retry invariant: retriesUsed, retryAttempts, and retrySuccesses each +1;
# budgetDenials unchanged. originalRequests is not asserted when expect_original_delta=-1.
# windowStartedAt/windowEndsAt are diagnostic only; they advance each second with the rolling
# window and must not be treated as stable window identity.
assert_retry_budget_field_deltas() {
  local before="$1"
  local after="$2"
  local api_id="$3"
  local route_id="$4"
  local policy_id="$5"
  local expect_original_delta="$6"
  local expect_retries_used_delta="$7"
  local expect_retry_attempts_delta="$8"
  local expect_retry_successes_delta="$9"
  local expect_budget_denials_delta="${10:-0}"
  python3 - "${before}" "${after}" \
    "${api_id}" "${route_id}" "${policy_id}" \
    "${expect_original_delta}" \
    "${expect_retries_used_delta}" \
    "${expect_retry_attempts_delta}" \
    "${expect_retry_successes_delta}" \
    "${expect_budget_denials_delta}" <<'PY'
import json
import sys

before = json.loads(sys.argv[1])
after = json.loads(sys.argv[2])
api_id, route_id, policy_id = sys.argv[3:6]
(
    expect_original_delta,
    expect_retries_used_delta,
    expect_retry_attempts_delta,
    expect_retry_successes_delta,
    expect_budget_denials_delta,
) = map(int, sys.argv[6:11])

def find_entry(payload):
    for entry in payload.get("budgets") or []:
        if (
            entry.get("apiId") == api_id
            and entry.get("routeId") == route_id
            and entry.get("policyId") == policy_id
        ):
            return entry
    print(json.dumps(payload, indent=2), file=sys.stderr)
    raise SystemExit(f"retry budget entry not found for route={route_id} policy={policy_id}")

def counters(payload):
    entry = find_entry(payload)
    return {
        "originalRequests": int(entry.get("originalRequests") or 0),
        "retriesUsed": int(entry.get("retriesUsed") or 0),
        "retryAttempts": int(entry.get("retryAttempts") or 0),
        "retrySuccesses": int(entry.get("retrySuccesses") or 0),
        "budgetDenials": int(entry.get("budgetDenials") or 0),
        "retryCapacity": int(entry.get("retryCapacity") or 0),
    }

before_entry = find_entry(before)
after_entry = find_entry(after)
before_counts = counters(before)
after_counts = counters(after)

if after_counts["retryCapacity"] != before_counts["retryCapacity"]:
    raise SystemExit(
        f"retryCapacity changed from {before_counts['retryCapacity']} to {after_counts['retryCapacity']}"
    )

for field in ("retriesUsed", "retryAttempts", "retrySuccesses", "budgetDenials"):
    if after_counts[field] < before_counts[field]:
        raise SystemExit(
            f"{field} decreased from {before_counts[field]} to {after_counts[field]}, "
            "likely destructive window rollover"
        )

checks = [
    ("originalRequests", expect_original_delta),
    ("retriesUsed", expect_retries_used_delta),
    ("retryAttempts", expect_retry_attempts_delta),
    ("retrySuccesses", expect_retry_successes_delta),
    ("budgetDenials", expect_budget_denials_delta),
]
for field, expected_delta in checks:
    if field == "originalRequests" and expected_delta < 0:
        continue
    actual_delta = after_counts[field] - before_counts[field]
    if actual_delta != expected_delta:
        raise SystemExit(
            f"{field} delta expected {expected_delta}, got {actual_delta} "
            f"(before={before_counts[field]} after={after_counts[field]})"
        )
PY
}

# Allowed retry invariant: per-request deltas of +1 for retriesUsed, retryAttempts, retrySuccesses.
# Args: before_json after_json api_id route_id policy_id
assert_retry_budget_allowed_retry_deltas() {
  local before="$1"
  local after="$2"
  local api_id="$3"
  local route_id="$4"
  local policy_id="$5"
  assert_retry_budget_field_deltas \
    "${before}" "${after}" \
    "${api_id}" "${route_id}" "${policy_id}" \
    -1 1 1 1 0
}

# Denied retry invariant: retry counters unchanged; budgetDenials +1.
# Args: before_json after_json api_id route_id policy_id
assert_retry_budget_denied_retry_deltas() {
  local before="$1"
  local after="$2"
  local api_id="$3"
  local route_id="$4"
  local policy_id="$5"
  assert_retry_budget_field_deltas \
    "${before}" "${after}" \
    "${api_id}" "${route_id}" "${policy_id}" \
    -1 0 0 0 1
}

read_retry_budget_window_bounds() {
  local retry_json="$1"
  local api_id="$2"
  local route_id="$3"
  local policy_id="$4"
  python3 - "${retry_json}" "${api_id}" "${route_id}" "${policy_id}" <<'PY'
import json
import sys

payload = json.loads(sys.argv[1])
api_id, route_id, policy_id = sys.argv[2:5]
for entry in payload.get("budgets") or []:
    if (
        entry.get("apiId") == api_id
        and entry.get("routeId") == route_id
        and entry.get("policyId") == policy_id
    ):
        print(f"{entry.get('windowStartedAt') or 'null'}|{entry.get('windowEndsAt') or 'null'}")
        raise SystemExit(0)
raise SystemExit(2)
PY
}

log_budget_retry_diagnostics() {
  local label="$1"
  local http_status="$2"
  local service="$3"
  local elapsed_ms="$4"
  local retry_before="$5"
  local retry_after="$6"
  local api_id="$7"
  local route_id="$8"
  local policy_id="$9"
  local before_counters after_counters
  local before_used before_capacity before_attempts before_successes
  local after_used after_capacity after_attempts after_successes
  local before_window after_window before_window_start before_window_end after_window_start after_window_end

  if ! before_counters="$(read_retry_budget_counters "${retry_before}" "${api_id}" "${route_id}" "${policy_id}")"; then
    echo "budget retry ${label}: failed to read before counters" >&2
    return 1
  fi
  if ! after_counters="$(read_retry_budget_counters "${retry_after}" "${api_id}" "${route_id}" "${policy_id}")"; then
    echo "budget retry ${label}: failed to read after counters" >&2
    return 1
  fi
  before_used="$(sed -n '2p' <<<"${before_counters}")"
  before_capacity="$(sed -n '3p' <<<"${before_counters}")"
  before_attempts="$(sed -n '4p' <<<"${before_counters}")"
  before_successes="$(sed -n '5p' <<<"${before_counters}")"
  after_used="$(sed -n '2p' <<<"${after_counters}")"
  after_capacity="$(sed -n '3p' <<<"${after_counters}")"
  after_attempts="$(sed -n '4p' <<<"${after_counters}")"
  after_successes="$(sed -n '5p' <<<"${after_counters}")"

  if before_window="$(read_retry_budget_window_bounds "${retry_before}" "${api_id}" "${route_id}" "${policy_id}")"; then
    IFS='|' read -r before_window_start before_window_end <<<"${before_window}"
  else
    before_window_start="unknown"
    before_window_end="unknown"
  fi
  if after_window="$(read_retry_budget_window_bounds "${retry_after}" "${api_id}" "${route_id}" "${policy_id}")"; then
    IFS='|' read -r after_window_start after_window_end <<<"${after_window}"
  else
    after_window_start="unknown"
    after_window_end="unknown"
  fi

  log_step "budget retry ${label}:"
  log_step "  HTTP ${http_status}"
  log_step "  service=${service}"
  log_step "  elapsedMs=${elapsed_ms}"
  log_step "  retriesUsed ${before_used} -> ${after_used}"
  log_step "  retryCapacity ${before_capacity} -> ${after_capacity}"
  log_step "  retryAttempts ${before_attempts} -> ${after_attempts}"
  log_step "  retrySuccesses ${before_successes} -> ${after_successes}"
  if [[ "${before_window_start}" != "${after_window_start}" || "${before_window_end}" != "${after_window_end}" ]]; then
    log_step "  rolling window bounds ${before_window_start}..${before_window_end} -> ${after_window_start}..${after_window_end} (diagnostic only)"
  fi
}

print_failover_proof_diagnostics() {
  local api_id="$1"
  local route_id="$2"
  local policy_id="$3"
  local retry_before="$4"
  local retry_after="$5"
  local http_status="$6"
  local service="$7"
  local duration_ms="$8"
  local proof_path="$9"
  local gateway_container="${10:-gateway-a}"

  echo "Failover proof diagnostics:" >&2
  echo "  routeId=${route_id}" >&2
  echo "  policyId=${policy_id}" >&2
  echo "  proofPath=${proof_path}" >&2
  echo "  httpStatus=${http_status}" >&2
  echo "  service=${service}" >&2
  echo "  elapsedMs=${duration_ms}" >&2
  echo "  retry-status before:" >&2
  printf '%s\n' "${retry_before}" >&2
  echo "  retry-status after:" >&2
  printf '%s\n' "${retry_after}" >&2
  log_retry_status_entry "before extracted" "${retry_before}" "${api_id}" "${route_id}" "${policy_id}" >&2 || true
  log_retry_status_entry "after extracted" "${retry_after}" "${api_id}" "${route_id}" "${policy_id}" >&2 || true
  if [[ -n "${proof_path}" ]]; then
    echo "  matching attempt logs:" >&2
    docker compose logs gateway-a --no-color --tail=300 2>/dev/null \
      | grep -F "${proof_path}" >&2 || true
    docker logs "${gateway_container}" --tail=300 2>/dev/null \
      | grep -F "${proof_path}" >&2 || true
  fi
}

assert_retry_counter_deltas() {
  local before="$1"
  local after="$2"
  local api_id="$3"
  local route_id="$4"
  local policy_id="$5"
  local expect_original_delta="$6"
  local expect_retries_used_delta="$7"
  local expect_retry_attempts_delta="$8"
  local expect_retry_successes_delta="$9"
  python3 - "${before}" "${after}" \
    "${api_id}" "${route_id}" "${policy_id}" \
    "${expect_original_delta}" \
    "${expect_retries_used_delta}" \
    "${expect_retry_attempts_delta}" \
    "${expect_retry_successes_delta}" <<'PY'
import json
import sys

before = json.loads(sys.argv[1])
after = json.loads(sys.argv[2])
api_id, route_id, policy_id = sys.argv[3:6]
(
    expect_original_delta,
    expect_retries_used_delta,
    expect_retry_attempts_delta,
    expect_retry_successes_delta,
) = map(int, sys.argv[6:10])

def find_entry(payload):
    for entry in payload.get("budgets") or []:
        if (
            entry.get("apiId") == api_id
            and entry.get("routeId") == route_id
            and entry.get("policyId") == policy_id
        ):
            return entry
    raise SystemExit("retry budget entry not found for counter delta assertion")

def counters(payload):
    entry = find_entry(payload)
    return {
        "originalRequests": int(entry.get("originalRequests") or 0),
        "retriesUsed": int(entry.get("retriesUsed") or 0),
        "retryAttempts": int(entry.get("retryAttempts") or 0),
        "retrySuccesses": int(entry.get("retrySuccesses") or 0),
    }

before_counts = counters(before)
after_counts = counters(after)
checks = [
    ("originalRequests", expect_original_delta),
    ("retriesUsed", expect_retries_used_delta),
    ("retryAttempts", expect_retry_attempts_delta),
    ("retrySuccesses", expect_retry_successes_delta),
]
for field, expected_delta in checks:
    actual_delta = after_counts[field] - before_counts[field]
    if actual_delta != expected_delta:
        raise SystemExit(
            f"{field} delta expected {expected_delta}, got {actual_delta} "
            f"(before={before_counts[field]} after={after_counts[field]})"
        )
PY
}

assert_published_retry_policy() {
  local snapshot_json="$1"
  python3 - "${snapshot_json}" <<'PY'
import json
import sys

payload = json.loads(sys.argv[1])
snapshot = payload.get("snapshot") or payload
routes = snapshot.get("routes") or []
if not routes:
    raise SystemExit("published snapshot has no routes")
route = routes[0]
retry = route.get("retry")
if retry is None:
    raise SystemExit("published snapshot route missing retry section")
if int(retry.get("maxAttempts") or 0) != 2:
    raise SystemExit(f"expected maxAttempts=2, got {retry.get('maxAttempts')}")
methods = retry.get("retryableMethods") or []
if "GET" not in methods:
    raise SystemExit("GET must be retryable in published snapshot")
if not retry.get("retryOnConnectFailure"):
    raise SystemExit("retryOnConnectFailure must be enabled")
targets = (route.get("upstreamPool") or {}).get("targets") or []
if len(targets) < 2:
    raise SystemExit(f"expected at least two upstream targets, got {len(targets)}")
PY
}

dump_compose_smoke_diagnostics() {
  local gateway_url="${1:-http://localhost:8080}"
  local control_plane_url="${2:-http://localhost:8081}"
  local api_id="${3:-}"

  log_step "Collecting compose smoke diagnostics"
  docker compose ps >&2 || true
  docker compose logs --no-color --tail=200 \
    gateway-a control-plane upstream-v1 upstream-v2 postgres redis \
    >&2 || true
  smoke_curl "${gateway_url}/internal/v1/retry-status" >&2 || true
  echo >&2
  smoke_curl "${gateway_url}/internal/v1/upstream-health" >&2 || true
  echo >&2
  if [[ -n "${api_id}" ]]; then
    smoke_curl "${control_plane_url}/api/v1/apis/${api_id}/convergence" >&2 || true
    echo >&2
  fi
}

print_retry_diagnostics() {
  dump_compose_smoke_diagnostics "$@"
}

dump_container_smoke_diagnostics() {
  local gateway_url="${1:-http://localhost:18081}"
  local control_plane_url="${2:-http://localhost:18080}"
  local api_id="${3:-}"

  log_step "Collecting container smoke diagnostics"
  docker ps -a --filter "network=${SMOKE_NETWORK:-autoapi-smoke}" >&2 || true
  if docker inspect autoapi-gateway >/dev/null 2>&1; then
    if declare -F smoke_redact_container_env >/dev/null; then
      docker logs autoapi-gateway --tail=200 2>&1 | smoke_redact_container_env >&2 || true
    else
      docker logs autoapi-gateway --tail=200 >&2 || true
    fi
    smoke_curl "${gateway_url}/internal/v1/retry-status" >&2 || true
    echo >&2
    smoke_curl "${gateway_url}/internal/v1/upstream-health" >&2 || true
    echo >&2
  fi
  if docker inspect autoapi-control-plane >/dev/null 2>&1; then
    if declare -F smoke_redact_container_env >/dev/null; then
      docker logs autoapi-control-plane --tail=100 2>&1 | smoke_redact_container_env >&2 || true
    else
      docker logs autoapi-control-plane --tail=100 >&2 || true
    fi
  fi
  if [[ -n "${api_id}" ]]; then
    smoke_curl "${control_plane_url}/api/v1/apis/${api_id}/convergence" >&2 || true
    echo >&2
  fi
}

collect_gateway_thread_dump() {
  local container_name="${1:-autoapi-gateway}"
  local gateway_pid

  if ! docker inspect "${container_name}" >/dev/null 2>&1; then
    return 0
  fi

  gateway_pid="$(docker inspect --format '{{.State.Pid}}' "${container_name}" 2>/dev/null || echo 0)"
  if [[ -n "${gateway_pid}" && "${gateway_pid}" != "0" ]]; then
    log_step "Sending SIGQUIT to ${container_name} for thread dump"
    docker kill --signal=QUIT "${container_name}" >/dev/null 2>&1 || true
    if declare -F smoke_redact_container_env >/dev/null; then
      docker logs "${container_name}" --tail=200 2>&1 | smoke_redact_container_env >&2 || true
    else
      docker logs "${container_name}" --tail=200 >&2 || true
    fi
  fi
}
