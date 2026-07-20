#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT}"

CONTROL_PLANE_URL="${CONTROL_PLANE_URL:-http://localhost:8081}"
SMOKE_SKIP_UP="${SMOKE_SKIP_UP:-false}"
ORG_ID="${DEFAULT_ORG_ID:-00000000-0000-0000-0000-000000000001}"
SMOKE_HEADERS_FILE=""
SMOKE_BODY_FILE=""

# shellcheck source=scripts/smoke-curl-lib.sh
source "${ROOT}/scripts/smoke-curl-lib.sh"
# shellcheck source=scripts/smoke-compose-lib.sh
source "${ROOT}/scripts/smoke-compose-lib.sh"
# shellcheck source=scripts/smoke-wait-lib.sh
source "${ROOT}/scripts/smoke-wait-lib.sh"
# shellcheck source=scripts/smoke-management-auth-lib.sh
source "${ROOT}/scripts/smoke-management-auth-lib.sh"

cleanup() {
  rm -f "${SMOKE_HEADERS_FILE}" "${SMOKE_BODY_FILE}"
  if [[ "${SMOKE_SKIP_UP:-false}" != "true" ]]; then
    timeout 120 docker compose down -v >/dev/null 2>&1 || true
  fi
}

trap cleanup EXIT

SMOKE_HEADERS_FILE="$(mktemp)"
SMOKE_BODY_FILE="$(mktemp)"

if [[ "${SMOKE_SKIP_UP}" != "true" ]]; then
  set_smoke_step "Starting compose stack"
  build_smoke_images_once
  start_smoke_base_stack
fi

set_smoke_step "Waiting for control plane"
wait_until "control-plane ready" 60 2 smoke_curl --fail "${CONTROL_PLANE_URL}/readyz" >/dev/null

set_smoke_step "Bootstrapping management administrator"
smoke_bootstrap_management "${CONTROL_PLANE_URL}"

set_smoke_step "Creating project and API"
PROJECT_SUFFIX="$(python3 -c 'import uuid; print(uuid.uuid4().hex[:8])')"
PROJECT_ID="$(control_plane_json "create project" \
  -X POST "${CONTROL_PLANE_URL}/api/v1/projects" \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"phase14-smoke-${PROJECT_SUFFIX}\",\"description\":\"phase 14 policy engine\"}" \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])')"

API_ID="$(control_plane_json "create api" \
  -X POST "${CONTROL_PLANE_URL}/api/v1/projects/${PROJECT_ID}/apis" \
  -H 'Content-Type: application/json' \
  -d '{"name":"policy-api","host":"api.autoapi.local","basePath":"/"}' \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])')"

POOL_ID="$(control_plane_json "create pool" \
  -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${API_ID}/upstream-pools" \
  -H 'Content-Type: application/json' \
  -d '{"name":"orders-v1","loadBalancing":"ROUND_ROBIN"}' \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])')"

control_plane_json "create target" \
  -X POST "${CONTROL_PLANE_URL}/api/v1/upstream-pools/${POOL_ID}/targets" \
  -H 'Content-Type: application/json' \
  -d '{"url":"http://upstream-v1:8080","enabled":true,"weight":1}' \
  >/dev/null

ROUTE_ID="$(control_plane_json "create route" \
  -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${API_ID}/routes" \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"orders\",\"host\":\"api.autoapi.local\",\"pathPrefix\":\"/v1/orders\",\"methods\":[\"GET\"],\"upstreamPoolId\":\"${POOL_ID}\",\"enabled\":true}" \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])')"

set_smoke_step "Creating policy bundle and revision"
BUNDLE_SUFFIX="$(python3 -c 'import uuid; print(uuid.uuid4().hex[:8])')"
BUNDLE_ID="$(control_plane_json "create bundle" \
  -X POST "${CONTROL_PLANE_URL}/api/v1/management/organizations/${ORG_ID}/policy-bundles" \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"standard-public-${BUNDLE_SUFFIX}\",\"description\":\"org defaults\"}" \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])')"

control_plane_json "create revision" \
  -X POST "${CONTROL_PLANE_URL}/api/v1/management/organizations/${ORG_ID}/policy-bundles/${BUNDLE_ID}/revisions" \
  -H 'Content-Type: application/json' \
  -d '{"message":"v1","content":{"rateLimit":{"limitCount":1000,"windowSeconds":60,"identitySource":"API_KEY","redisFailureMode":"FAIL_OPEN"},"timeout":{"timeoutMs":10000},"headers":{"X-Policy-Revision":"v1"}}}' \
  >/dev/null

set_smoke_step "Assigning bundle at organization scope"
ASSIGNMENT_ID="$(control_plane_json "assign bundle" \
  -X POST "${CONTROL_PLANE_URL}/api/v1/management/organizations/${ORG_ID}/policy-bundles/${BUNDLE_ID}/assignments" \
  -H 'Content-Type: application/json' \
  -d '{"revisionNumber":1}' \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])')"

set_smoke_step "Applying API overrides for timeout and rate limit"
control_plane_json "override timeout" \
  -X POST "${CONTROL_PLANE_URL}/api/v1/management/apis/${API_ID}/policy-overrides" \
  -H 'Content-Type: application/json' \
  -d '{"policyType":"timeout","mode":"OVERRIDE","content":{"timeoutMs":3000}}' \
  >/dev/null

control_plane_json "override rate limit" \
  -X POST "${CONTROL_PLANE_URL}/api/v1/management/apis/${API_ID}/policy-overrides" \
  -H 'Content-Type: application/json' \
  -d '{"policyType":"rateLimit","mode":"OVERRIDE","content":{"limitCount":500,"windowSeconds":60,"identitySource":"API_KEY","redisFailureMode":"FAIL_OPEN"}}' \
  >/dev/null

set_smoke_step "Evaluating effective policy with explain mode"
control_plane_json "effective policy" \
  "${CONTROL_PLANE_URL}/api/v1/management/apis/${API_ID}/effective-policy?routeId=${ROUTE_ID}&explain=true" \
  | python3 -c 'import json,sys; p=json.load(sys.stdin); assert p["rateLimit"]["limitCount"]==500; assert p["timeout"]["timeoutMs"]==3000; assert p["headers"]["X-Policy-Revision"]=="v1"; assert any(e.get("policyType")=="timeout" for e in p.get("explanations",[]))'

control_plane_json "dry-run evaluate" \
  -X POST "${CONTROL_PLANE_URL}/api/v1/management/policies/evaluate" \
  -H 'Content-Type: application/json' \
  -d "{\"apiId\":\"${API_ID}\",\"routeId\":\"${ROUTE_ID}\",\"explain\":true}" \
  | python3 -c 'import json,sys; p=json.load(sys.stdin); assert p["rateLimit"]["limitCount"]==500; assert p["headers"]["X-Policy-Revision"]=="v1"'

set_smoke_step "Publishing configuration with flattened effective policy"
control_plane_json "publish config" \
  -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${API_ID}/config/versions" \
  -H 'Content-Type: application/json' \
  -d '{"message":"phase14 effective policy"}' \
  >/dev/null

control_plane_json "activate config" \
  -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${API_ID}/config/versions/1/activate" \
  -H 'Content-Type: application/json' \
  -d '{}' \
  >/dev/null

control_plane_json "gateway snapshot" \
  "${CONTROL_PLANE_URL}/api/v1/gateway-config/${API_ID}/versions/1" \
  | python3 -c 'import json,sys; p=json.load(sys.stdin); assert p["routes"][0]["rateLimit"]["limitCount"]==500; assert p["effectivePolicies"][0]["policies"]["rateLimit"]["limitCount"]==500'

set_smoke_step "Updating bundle revision invalidates effective policy cache"
control_plane_json "create revision 2" \
  -X POST "${CONTROL_PLANE_URL}/api/v1/management/organizations/${ORG_ID}/policy-bundles/${BUNDLE_ID}/revisions" \
  -H 'Content-Type: application/json' \
  -d '{"message":"v2","content":{"rateLimit":{"limitCount":750,"windowSeconds":60,"identitySource":"API_KEY","redisFailureMode":"FAIL_OPEN"},"headers":{"X-Policy-Revision":"v2"},"retry":{"maxAttempts":4,"perAttemptTimeoutMs":1500,"retryOnConnectFailure":true,"retryOnConnectionReset":true,"retryOnDnsFailure":true,"retryOnResponseTimeout":false,"retryableMethods":["GET"],"requireIdempotencyKeyForUnsafeMethods":true,"budgetPercent":10,"budgetMinRetriesPerSecond":1,"budgetWindowSeconds":1}}}' \
  >/dev/null

control_plane_json "upgrade bundle assignment revision" \
  -X PATCH "${CONTROL_PLANE_URL}/api/v1/management/policy-bundle-assignments/${ASSIGNMENT_ID}" \
  -H 'Content-Type: application/json' \
  -d '{"revisionNumber":2}' \
  | python3 -c 'import json,sys; p=json.load(sys.stdin); assert p["id"]=="'"${ASSIGNMENT_ID}"'"; assert p["revisionNumber"]==2'

control_plane_json "effective policy after bundle update" \
  "${CONTROL_PLANE_URL}/api/v1/management/apis/${API_ID}/effective-policy?routeId=${ROUTE_ID}&explain=true" \
  | python3 -c 'import json,sys; p=json.load(sys.stdin); assert p["rateLimit"]["limitCount"]==500; assert p["headers"]["X-Policy-Revision"]=="v2"; headers=[e for e in p.get("explanations",[]) if e.get("policyType")=="headers"]; assert headers and headers[0].get("winningRevision")==2'

control_plane_json "publish config after revision upgrade" \
  -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${API_ID}/config/versions" \
  -H 'Content-Type: application/json' \
  -d '{"message":"phase14 revision 2 policy"}' \
  >/dev/null

control_plane_json "activate config v2" \
  -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${API_ID}/config/versions/2/activate" \
  -H 'Content-Type: application/json' \
  -d '{"expectedDesiredVersion":1}' \
  >/dev/null

control_plane_json "gateway snapshot after revision upgrade" \
  "${CONTROL_PLANE_URL}/api/v1/gateway-config/${API_ID}/versions/2" \
  | python3 -c 'import json,sys; p=json.load(sys.stdin); assert p["routes"][0]["rateLimit"]["limitCount"]==500; assert p["effectivePolicies"][0]["policies"]["headers"]["X-Policy-Revision"]=="v2"'

set_smoke_step "Duplicate enabled assignment returns 409 RESOURCE_CONFLICT"
if ! http_request_capture "${SMOKE_BODY_FILE}" \
  -H "Authorization: Bearer ${MANAGEMENT_TOKEN}" \
  -X POST "${CONTROL_PLANE_URL}/api/v1/management/organizations/${ORG_ID}/policy-bundles/${BUNDLE_ID}/assignments" \
  -H 'Content-Type: application/json' \
  -d '{"revisionNumber":2}'; then
  echo "Duplicate assignment transport failure curl_exit=${HTTP_CURL_EXIT}" >&2
  exit 1
fi
expect_http_status "${HTTP_STATUS}" 409
python3 - "${SMOKE_BODY_FILE}" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as handle:
    document = json.load(handle)
code = document.get("error", {}).get("code")
if code != "RESOURCE_CONFLICT":
    raise SystemExit(f"expected RESOURCE_CONFLICT, got {code!r}")
PY

set_smoke_step "Creating viewer service account for RBAC checks"
SA_ID="$(control_plane_json "create viewer sa" \
  -X POST "${CONTROL_PLANE_URL}/api/v1/management/organizations/${ORG_ID}/service-accounts" \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"phase14-viewer\",\"description\":\"viewer\",\"projectId\":\"${PROJECT_ID}\"}" \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])')"

control_plane_json "bind viewer role" \
  -X POST "${CONTROL_PLANE_URL}/api/v1/management/projects/${PROJECT_ID}/role-bindings" \
  -H 'Content-Type: application/json' \
  -d "{\"principalType\":\"SERVICE_ACCOUNT\",\"principalId\":\"${SA_ID}\",\"role\":\"PROJECT_VIEWER\"}" \
  >/dev/null

VIEWER_TOKEN="$(control_plane_json "create viewer credential" \
  -X POST "${CONTROL_PLANE_URL}/api/v1/management/service-accounts/${SA_ID}/credentials" \
  -H 'Content-Type: application/json' \
  -d '{"name":"viewer-token","scopes":["project.read","policy.effective.read"]}' \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["token"])')"

set_smoke_step "Viewer cannot create policy bundles"
if ! http_request_capture "${SMOKE_BODY_FILE}" \
  -H "Authorization: Bearer ${VIEWER_TOKEN}" \
  -X POST "${CONTROL_PLANE_URL}/api/v1/management/organizations/${ORG_ID}/policy-bundles" \
  -H 'Content-Type: application/json' \
  -d '{"name":"blocked","description":"forbidden"}'; then
  echo "Viewer bundle create transport failure curl_exit=${HTTP_CURL_EXIT}" >&2
  exit 1
fi
expect_http_status "${HTTP_STATUS}" 403

set_smoke_step "Viewer cannot upgrade policy bundle assignments"
if ! http_request_capture "${SMOKE_BODY_FILE}" \
  -H "Authorization: Bearer ${VIEWER_TOKEN}" \
  -X PATCH "${CONTROL_PLANE_URL}/api/v1/management/policy-bundle-assignments/${ASSIGNMENT_ID}" \
  -H 'Content-Type: application/json' \
  -d '{"revisionNumber":2}'; then
  echo "Viewer assignment upgrade transport failure curl_exit=${HTTP_CURL_EXIT}" >&2
  exit 1
fi
expect_http_status "${HTTP_STATUS}" 403

log_step "Phase 14 policy engine smoke passed"
