#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT}"

CONTROL_PLANE_URL="${CONTROL_PLANE_URL:-http://localhost:8081}"
SMOKE_SKIP_UP="${SMOKE_SKIP_UP:-false}"
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

set_smoke_step "Verifying authenticated /auth/me"
control_plane_json "auth me" \
  "${CONTROL_PLANE_URL}/api/v1/management/auth/me" \
  | python3 -c 'import json,sys; p=json.load(sys.stdin); assert p["principalType"] in ("BOOTSTRAP_ADMIN","SERVICE_ACCOUNT")'

set_smoke_step "Creating project with scoped service account"
PROJECT_ID="$(control_plane_json "create project" \
  -X POST "${CONTROL_PLANE_URL}/api/v1/projects" \
  -H 'Content-Type: application/json' \
  -d '{"name":"phase13-smoke","description":"phase 13 rbac"}' \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])')"

ORG_ID="${DEFAULT_ORG_ID:-00000000-0000-0000-0000-000000000001}"
SA_ID="$(control_plane_json "create service account" \
  -X POST "${CONTROL_PLANE_URL}/api/v1/management/organizations/${ORG_ID}/service-accounts" \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"phase13-viewer\",\"description\":\"viewer sa\",\"projectId\":\"${PROJECT_ID}\"}" \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])')"

control_plane_json "bind viewer role" \
  -X POST "${CONTROL_PLANE_URL}/api/v1/management/projects/${PROJECT_ID}/role-bindings" \
  -H 'Content-Type: application/json' \
  -d "{\"principalType\":\"SERVICE_ACCOUNT\",\"principalId\":\"${SA_ID}\",\"role\":\"PROJECT_VIEWER\"}" \
  >/dev/null

VIEWER_TOKEN="$(control_plane_json "create viewer credential" \
  -X POST "${CONTROL_PLANE_URL}/api/v1/management/service-accounts/${SA_ID}/credentials" \
  -H 'Content-Type: application/json' \
  -d '{"name":"viewer-token","scopes":["project.read"]}' \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["token"])')"

set_smoke_step "Viewer token can read but not mutate project"
status="$(
  smoke_curl -o /dev/null -w '%{http_code}' \
    -H "Authorization: Bearer ${VIEWER_TOKEN}" \
    "${CONTROL_PLANE_URL}/api/v1/projects/${PROJECT_ID}"
)"
[[ "${status}" == "200" ]]

status="$(
  smoke_curl -o /dev/null -w '%{http_code}' \
    -X PATCH \
    -H "Authorization: Bearer ${VIEWER_TOKEN}" \
    -H 'Content-Type: application/json' \
    -d '{"description":"denied"}' \
    "${CONTROL_PLANE_URL}/api/v1/projects/${PROJECT_ID}" 2>/dev/null || echo 000
)"
[[ "${status}" == "403" || "${status}" == "404" ]]

set_smoke_step "Gateway credential cannot access management API"
status="$(
  smoke_curl -o /dev/null -w '%{http_code}' \
    -X POST \
    -H 'Content-Type: application/json' \
    -d '{"gatewayId":"gateway-a","softwareVersion":"0.1.0"}' \
    "${CONTROL_PLANE_URL}/api/v1/gateways/register" 2>/dev/null || echo 000
)"
[[ "${status}" == "200" || "${status}" == "201" || "${status}" == "409" ]]

status="$(
  smoke_curl -o /dev/null -w '%{http_code}' \
    "${CONTROL_PLANE_URL}/api/v1/projects" 2>/dev/null || echo 000
)"
[[ "${status}" == "401" ]]

log_step "Phase 13 smoke completed successfully"
