#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT}"

CONTROL_PLANE_URL="${CONTROL_PLANE_URL:-http://localhost:8081}"
GATEWAY_A_URL="${GATEWAY_A_URL:-http://localhost:8080}"
GATEWAY_B_URL="${GATEWAY_B_URL:-http://localhost:8082}"
GATEWAY_C_URL="${GATEWAY_C_URL:-http://localhost:8083}"
SMOKE_SKIP_UP="${SMOKE_SKIP_UP:-false}"
PHASE12_API_ID=""
PHASE12_PROJECT_ID=""

SMOKE_STARTED_AT="$(date +%s)"
SMOKE_HEADERS_FILE=""
SMOKE_BODY_FILE=""

# shellcheck source=scripts/smoke-curl-lib.sh
source "${ROOT}/scripts/smoke-curl-lib.sh"
# shellcheck source=scripts/smoke-compose-lib.sh
source "${ROOT}/scripts/smoke-compose-lib.sh"
# shellcheck source=scripts/smoke-wait-lib.sh
source "${ROOT}/scripts/smoke-wait-lib.sh"

cleanup() {
  rm -f "${SMOKE_HEADERS_FILE}" "${SMOKE_BODY_FILE}"
  if [[ "${SMOKE_SKIP_UP:-false}" != "true" ]]; then
    timeout 120 docker compose down -v >/dev/null 2>&1 || true
  fi
}

dump_diagnostics() {
  local exit_code="$?"
  if [[ "${exit_code}" -ne 0 ]]; then
    log_step "Phase 12 smoke failed at step: ${SMOKE_CURRENT_STEP}"
    dump_compose_smoke_diagnostics "${GATEWAY_A_URL}" "${CONTROL_PLANE_URL}" "${PHASE12_API_ID}"
  fi
  cleanup
  return "${exit_code}"
}

json_field() {
  python3 - "$1" "$2" <<'PY'
import json, sys
payload = json.loads(sys.argv[1])
print(payload[sys.argv[2]])
PY
}

control_plane_json() {
  local context="$1"
  shift
  local status curl_exit
  set +e
  status="$(
    smoke_curl \
      -D "${SMOKE_HEADERS_FILE}" \
      -o "${SMOKE_BODY_FILE}" \
      -w '%{http_code}' \
      "$@"
  )"
  curl_exit=$?
  set -e
  if [[ ${curl_exit} -ne 0 || "${status}" -lt 200 || "${status}" -ge 300 ]]; then
    report_curl_failure "${context}" "${curl_exit}" "${status}"
    cat "${SMOKE_HEADERS_FILE}" >&2 || true
    cat "${SMOKE_BODY_FILE}" >&2 || true
    exit 1
  fi
  cat "${SMOKE_BODY_FILE}"
}

rollout_status() {
  local project_id="$1"
  local rollout_id="$2"
  smoke_curl --fail --silent \
    "${CONTROL_PLANE_URL}/api/v1/management/projects/${project_id}/rollouts/${rollout_id}" \
    | python3 -c 'import json,sys; print(json.load(sys.stdin)["status"])'
}

assigned_gateway_count() {
  local project_id="$1"
  local rollout_id="$2"
  smoke_curl --fail --silent \
    "${CONTROL_PLANE_URL}/api/v1/management/projects/${project_id}/rollouts/${rollout_id}/assignments?limit=50" \
    | python3 -c 'import json,sys; print(sum(1 for a in json.load(sys.stdin) if a.get("status")=="ASSIGNED"))'
}

label_gateways_for_rollout() {
  docker compose exec -T postgres psql -U autoapi -d autoapi -c \
    "UPDATE gateways SET admin_labels = '{\"region\":\"us-west\",\"environment\":\"production\"}'::jsonb, runtime_schema_version = 1 WHERE id IN ('gateway-a','gateway-b','gateway-c');" \
    >/dev/null
}

trap dump_diagnostics EXIT

if [[ "${SMOKE_SKIP_UP}" != "true" ]]; then
  set_smoke_step "Starting compose stack"
  build_smoke_images_once
  start_smoke_base_stack
  start_smoke_gateways gateway-a gateway-b gateway-c
fi

set_smoke_step "Waiting for services"
wait_until "control-plane ready" 60 2 smoke_curl --fail "${CONTROL_PLANE_URL}/readyz" >/dev/null
wait_until "gateway-a ready" 60 2 smoke_curl --fail "${GATEWAY_A_URL}/readyz" >/dev/null
wait_until "gateway-b ready" 60 2 smoke_curl --fail "${GATEWAY_B_URL}/readyz" >/dev/null
wait_until "gateway-c ready" 60 2 smoke_curl --fail "${GATEWAY_C_URL}/readyz" >/dev/null

set_smoke_step "Creating project and API"
PHASE12_PROJECT_ID="$(control_plane_json "create project" \
  -X POST "${CONTROL_PLANE_URL}/api/v1/projects" \
  -H 'Content-Type: application/json' \
  -d '{"name":"phase12-smoke","description":"phase 12 rollouts"}' \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])')"
PHASE12_API_ID="$(control_plane_json "create api" \
  -X POST "${CONTROL_PLANE_URL}/api/v1/projects/${PHASE12_PROJECT_ID}/apis" \
  -H 'Content-Type: application/json' \
  -d '{"name":"phase12-api","host":"api.autoapi.local","basePath":"/"}' \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])')"

set_smoke_step "Configuring upstream pool and routes"
POOL_ID="$(control_plane_json "create pool" \
  -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${PHASE12_API_ID}/upstream-pools" \
  -H 'Content-Type: application/json' \
  -d '{"name":"primary","loadBalancing":"ROUND_ROBIN"}' \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])')"
control_plane_json "add target" \
  -X POST "${CONTROL_PLANE_URL}/api/v1/upstream-pools/${POOL_ID}/targets" \
  -H 'Content-Type: application/json' \
  -d '{"url":"http://upstream-v1:8080","enabled":true,"weight":1}' >/dev/null
control_plane_json "add v1 route" \
  -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${PHASE12_API_ID}/routes" \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"orders-v1\",\"host\":\"api.autoapi.local\",\"pathPrefix\":\"/v1/orders\",\"methods\":[\"GET\"],\"upstreamPoolId\":\"${POOL_ID}\",\"enabled\":true}" >/dev/null

set_smoke_step "Publishing and activating v1"
control_plane_json "publish v1" \
  -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${PHASE12_API_ID}/config/versions" \
  -H 'Content-Type: application/json' \
  -d '{"message":"version 1"}' >/dev/null
control_plane_json "activate v1" \
  -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${PHASE12_API_ID}/config/versions/1/activate" \
  -H 'Content-Type: application/json' \
  -d '{"expectedDesiredVersion":null}' >/dev/null

set_smoke_step "Publishing v2"
control_plane_json "add v2 route" \
  -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${PHASE12_API_ID}/routes" \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"orders-v2\",\"host\":\"api.autoapi.local\",\"pathPrefix\":\"/v2/orders\",\"methods\":[\"GET\"],\"upstreamPoolId\":\"${POOL_ID}\",\"enabled\":true}" >/dev/null
control_plane_json "publish v2" \
  -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${PHASE12_API_ID}/config/versions" \
  -H 'Content-Type: application/json' \
  -d '{"message":"version 2"}' >/dev/null

set_smoke_step "Labeling gateways for group selector"
label_gateways_for_rollout

set_smoke_step "Creating gateway group"
GROUP_JSON="$(control_plane_json "create gateway group" \
  -X POST "${CONTROL_PLANE_URL}/api/v1/management/projects/${PHASE12_PROJECT_ID}/gateway-groups" \
  -H 'Content-Type: application/json' \
  -d "{\"apiId\":\"${PHASE12_API_ID}\",\"name\":\"prod-us-west\",\"selectorJson\":\"{\\\"matchLabels\\\":{\\\"region\\\":\\\"us-west\\\",\\\"environment\\\":\\\"production\\\"}}\",\"enabled\":true}")"
GROUP_ID="$(json_field "${GROUP_JSON}" id)"

STAGES='[
  {"percentage":33,"minimumGatewayCount":1,"requiredConvergedPercentage":100,"maximumFailedGateways":0,"maximumTimedOutGateways":0,"requiredOnlinePercentage":0,"observationDurationMs":1000,"stageTimeoutMs":60000},
  {"percentage":50,"minimumGatewayCount":1,"requiredConvergedPercentage":100,"maximumFailedGateways":0,"maximumTimedOutGateways":0,"requiredOnlinePercentage":0,"observationDurationMs":1000,"stageTimeoutMs":60000},
  {"percentage":100,"minimumGatewayCount":1,"requiredConvergedPercentage":100,"maximumFailedGateways":0,"maximumTimedOutGateways":0,"requiredOnlinePercentage":0,"observationDurationMs":1000,"stageTimeoutMs":60000}
]'

set_smoke_step "Previewing rollout plan"
PREVIEW_JSON="$(control_plane_json "preview rollout" \
  -X POST "${CONTROL_PLANE_URL}/api/v1/management/projects/${PHASE12_PROJECT_ID}/rollouts/preview" \
  -H 'Content-Type: application/json' \
  -d "{\"gatewayGroupId\":\"${GROUP_ID}\",\"targetVersion\":2,\"strategy\":\"PROGRESSIVE_PERCENTAGE\",\"stages\":${STAGES}}")"
python3 - "${PREVIEW_JSON}" <<'PY'
import json, sys
preview = json.loads(sys.argv[1])
assert preview["eligibleGatewayCount"] == 3, preview
counts = [stage["gatewayCount"] for stage in preview["stageGatewayCounts"]]
assert counts == [1, 2, 3], counts
PY

set_smoke_step "Creating and starting rollout"
ROLLOUT_JSON="$(control_plane_json "create rollout" \
  -X POST "${CONTROL_PLANE_URL}/api/v1/management/projects/${PHASE12_PROJECT_ID}/rollouts" \
  -H 'Content-Type: application/json' \
  -d "{\"gatewayGroupId\":\"${GROUP_ID}\",\"targetVersion\":2,\"strategy\":\"PROGRESSIVE_PERCENTAGE\",\"progressionMode\":\"MANUAL\",\"autoRollbackOnFailure\":false,\"cancelBehavior\":\"KEEP_CURRENT_ASSIGNMENTS\",\"stages\":${STAGES}}")"
ROLLOUT_ID="$(json_field "${ROLLOUT_JSON}" id)"
control_plane_json "start rollout" \
  -X POST "${CONTROL_PLANE_URL}/api/v1/management/projects/${PHASE12_PROJECT_ID}/rollouts/${ROLLOUT_ID}/start" \
  -H 'Content-Type: application/json' \
  -d '{}' >/dev/null

wait_until "rollout running" 30 0.5 test "$(rollout_status "${PHASE12_PROJECT_ID}" "${ROLLOUT_ID}")" = "RUNNING"
wait_until "first cohort assigned" 30 0.5 test "$(assigned_gateway_count "${PHASE12_PROJECT_ID}" "${ROLLOUT_ID}")" -ge 1

set_smoke_step "Verifying desired config for assigned gateway"
FIRST_ASSIGNED="$(smoke_curl --fail --silent \
  "${CONTROL_PLANE_URL}/api/v1/management/projects/${PHASE12_PROJECT_ID}/rollouts/${ROLLOUT_ID}/assignments?limit=50" \
  | python3 -c 'import json,sys; assigned=[a["gatewayId"] for a in json.load(sys.stdin) if a.get("status")=="ASSIGNED"]; print(assigned[0] if assigned else "")')"
if [[ -z "${FIRST_ASSIGNED}" ]]; then
  echo "Expected at least one assigned gateway" >&2
  exit 1
fi
DESIRED_VERSION="$(smoke_curl --fail --silent \
  "${CONTROL_PLANE_URL}/api/v1/gateway-config/${PHASE12_API_ID}/desired?gatewayId=${FIRST_ASSIGNED}" \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["version"])')"
if [[ "${DESIRED_VERSION}" != "2" ]]; then
  echo "Expected assigned gateway desired version 2, got ${DESIRED_VERSION}" >&2
  exit 1
fi

set_smoke_step "Pausing and resuming rollout"
control_plane_json "pause rollout" \
  -X POST "${CONTROL_PLANE_URL}/api/v1/management/projects/${PHASE12_PROJECT_ID}/rollouts/${ROLLOUT_ID}/pause" \
  -H 'Content-Type: application/json' \
  -d '{}' >/dev/null
wait_until "rollout paused" 15 0.5 test "$(rollout_status "${PHASE12_PROJECT_ID}" "${ROLLOUT_ID}")" = "PAUSED"
control_plane_json "resume rollout" \
  -X POST "${CONTROL_PLANE_URL}/api/v1/management/projects/${PHASE12_PROJECT_ID}/rollouts/${ROLLOUT_ID}/resume" \
  -H 'Content-Type: application/json' \
  -d '{}' >/dev/null
wait_until "rollout running again" 15 0.5 test "$(rollout_status "${PHASE12_PROJECT_ID}" "${ROLLOUT_ID}")" = "RUNNING"

set_smoke_step "Advancing rollout stages"
control_plane_json "advance stage 2" \
  -X POST "${CONTROL_PLANE_URL}/api/v1/management/projects/${PHASE12_PROJECT_ID}/rollouts/${ROLLOUT_ID}/advance" \
  -H 'Content-Type: application/json' \
  -d '{}' >/dev/null
wait_until "two gateways assigned" 30 0.5 test "$(assigned_gateway_count "${PHASE12_PROJECT_ID}" "${ROLLOUT_ID}")" -ge 2
control_plane_json "advance stage 3" \
  -X POST "${CONTROL_PLANE_URL}/api/v1/management/projects/${PHASE12_PROJECT_ID}/rollouts/${ROLLOUT_ID}/advance" \
  -H 'Content-Type: application/json' \
  -d '{}' >/dev/null
wait_until "three gateways assigned" 30 0.5 test "$(assigned_gateway_count "${PHASE12_PROJECT_ID}" "${ROLLOUT_ID}")" -ge 3
control_plane_json "complete rollout" \
  -X POST "${CONTROL_PLANE_URL}/api/v1/management/projects/${PHASE12_PROJECT_ID}/rollouts/${ROLLOUT_ID}/advance" \
  -H 'Content-Type: application/json' \
  -d '{}' >/dev/null
wait_until "rollout succeeded" 30 0.5 test "$(rollout_status "${PHASE12_PROJECT_ID}" "${ROLLOUT_ID}")" = "SUCCEEDED"

set_smoke_step "Verifying group desired version"
GROUP_DESIRED="$(smoke_curl --fail --silent \
  "${CONTROL_PLANE_URL}/api/v1/management/projects/${PHASE12_PROJECT_ID}/gateway-groups/${GROUP_ID}" \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["desiredConfigVersion"])')"
if [[ "${GROUP_DESIRED}" != "2" ]]; then
  echo "Expected gateway group desired version 2, got ${GROUP_DESIRED}" >&2
  exit 1
fi

log_step "Phase 12 smoke completed successfully"
