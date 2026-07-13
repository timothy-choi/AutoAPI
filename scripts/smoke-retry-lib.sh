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
  docker logs autoapi-gateway --tail=200 >&2 || true
  docker logs autoapi-control-plane --tail=100 >&2 || true
  smoke_curl "${gateway_url}/internal/v1/retry-status" >&2 || true
  echo >&2
  smoke_curl "${gateway_url}/internal/v1/upstream-health" >&2 || true
  echo >&2
  if [[ -n "${api_id}" ]]; then
    smoke_curl "${control_plane_url}/api/v1/apis/${api_id}/convergence" >&2 || true
    echo >&2
  fi
}

collect_gateway_thread_dump() {
  local container_name="${1:-autoapi-gateway}"
  local gateway_pid
  gateway_pid="$(docker inspect --format '{{.State.Pid}}' "${container_name}" 2>/dev/null || echo 0)"
  if [[ -n "${gateway_pid}" && "${gateway_pid}" != "0" ]]; then
    log_step "Sending SIGQUIT to ${container_name} for thread dump"
    docker kill --signal=QUIT "${container_name}" >/dev/null 2>&1 || true
    docker logs "${container_name}" --tail=200 >&2 || true
  fi
}
