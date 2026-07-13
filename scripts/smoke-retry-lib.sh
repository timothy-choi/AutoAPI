#!/usr/bin/env bash
# Retry-status and snapshot assertions for Phase 6 smoke scripts.

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
        capacity = int(entry.get("retryCapacity") or 0)
        if originals >= 1 and capacity >= 1:
            raise SystemExit(0)
        raise SystemExit(
            f"retry budget entry found but under-initialized: originals={originals} capacity={capacity}"
        )
raise SystemExit("retry budget entry not found for active route/policy")
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

print_retry_diagnostics() {
  local gateway_url="$1"
  local control_plane_url="$2"
  local api_id="${3:-}"
  echo "== Retry smoke diagnostics ==" >&2
  curl --silent "${gateway_url}/internal/v1/retry-status" >&2 || true
  echo >&2
  curl --silent "${gateway_url}/internal/v1/upstream-health" >&2 || true
  echo >&2
  if [[ -n "${api_id}" ]]; then
    curl --silent "${control_plane_url}/api/v1/apis/${api_id}/convergence" >&2 || true
    echo >&2
  fi
  docker ps -a --filter "network=${SMOKE_NETWORK:-autoapi-smoke}" >&2 || true
  docker logs autoapi-gateway --tail 40 >&2 || true
}
