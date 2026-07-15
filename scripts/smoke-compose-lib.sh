#!/usr/bin/env bash
# Shared Docker Compose helpers for integration smoke scripts.

# shellcheck source=scripts/smoke-curl-lib.sh
if [[ -z "${SMOKE_CURL_LIB_LOADED:-}" ]]; then
  SMOKE_CURL_LIB_LOADED=1
  _SMOKE_COMPOSE_LIB_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
  # shellcheck source=scripts/smoke-curl-lib.sh
  source "${_SMOKE_COMPOSE_LIB_DIR}/smoke-curl-lib.sh"
fi

AUTOAPI_SMOKE_IMAGE="${AUTOAPI_SMOKE_IMAGE:-autoapi-server:smoke}"
MOCK_UPSTREAM_SMOKE_IMAGE="${MOCK_UPSTREAM_SMOKE_IMAGE:-mock-upstream:smoke}"
SMOKE_IMAGES_BUILT="${SMOKE_IMAGES_BUILT:-false}"

build_smoke_images_once() {
  if [[ "${SMOKE_IMAGES_BUILT}" == "true" ]]; then
    log_step "Smoke images already built; skipping rebuild"
    return 0
  fi

  if [[ -n "${CANDIDATE_IMAGE:-}" ]]; then
    log_step "Using prebuilt candidate image ${CANDIDATE_IMAGE} as ${AUTOAPI_SMOKE_IMAGE}"
    docker tag "${CANDIDATE_IMAGE}" "${AUTOAPI_SMOKE_IMAGE}"
  else
    log_step "Building AutoAPI smoke image ${AUTOAPI_SMOKE_IMAGE}"
    docker build -t "${AUTOAPI_SMOKE_IMAGE}" -f Server/Dockerfile Server
  fi

  if [[ -n "${MOCK_UPSTREAM_IMAGE:-}" ]] && docker image inspect "${MOCK_UPSTREAM_IMAGE}" >/dev/null 2>&1; then
    log_step "Tagging existing mock upstream ${MOCK_UPSTREAM_IMAGE} as ${MOCK_UPSTREAM_SMOKE_IMAGE}"
    docker tag "${MOCK_UPSTREAM_IMAGE}" "${MOCK_UPSTREAM_SMOKE_IMAGE}"
  else
    log_step "Building mock-upstream smoke image ${MOCK_UPSTREAM_SMOKE_IMAGE}"
    docker build -t "${MOCK_UPSTREAM_SMOKE_IMAGE}" tests/mock-upstream
  fi

  SMOKE_IMAGES_BUILT=true
}

start_smoke_base_stack() {
  log_step "Starting PostgreSQL, Redis, upstreams, and control plane (no rebuild)"
  docker compose up -d postgres redis upstream-v1 upstream-v2 control-plane
}

start_smoke_gateways() {
  log_step "Starting gateways without recreating dependencies: $*"
  docker compose up -d --no-deps "$@"
}

compose_container_id() {
  docker compose ps -q "$1" 2>/dev/null | head -n 1
}

assert_compose_container_unchanged() {
  local service="$1"
  local expected_id="$2"
  local actual_id
  actual_id="$(compose_container_id "${service}")"
  if [[ -z "${actual_id}" || "${actual_id}" != "${expected_id}" ]]; then
    echo "Container ${service} was recreated or missing (expected ${expected_id}, got ${actual_id:-none})" >&2
    exit 1
  fi
}

dump_compose_smoke_diagnostics() {
  local gateway_url="${1:-http://localhost:8080}"
  local control_plane_url="${2:-http://localhost:8081}"

  log_step "Collecting compose smoke diagnostics (step=${CURRENT_STEP:-${SMOKE_CURRENT_STEP:-unknown}})"
  if [[ -n "${SMOKE_STARTED_AT:-}" ]]; then
    log_step "Total elapsed=$(( $(date +%s) - SMOKE_STARTED_AT ))s"
  fi
  docker compose ps >&2 || true
  smoke_curl "${gateway_url}/readyz" >/dev/null 2>&1 || true
  smoke_curl "${control_plane_url}/readyz" >/dev/null 2>&1 || true
  docker compose logs --no-color --tail=200 gateway-a gateway-b control-plane upstream-v1 upstream-v2 \
    >&2 || true
}
