#!/usr/bin/env bash
# Bounded polling helpers for smoke scripts.

# shellcheck source=scripts/smoke-curl-lib.sh
if [[ -z "${SMOKE_CURL_LIB_LOADED:-}" ]]; then
  SMOKE_CURL_LIB_LOADED=1
  _SMOKE_WAIT_LIB_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
  # shellcheck source=scripts/smoke-curl-lib.sh
  source "${_SMOKE_WAIT_LIB_DIR}/smoke-curl-lib.sh"
fi

wait_until() {
  local description="$1"
  local attempts="$2"
  local delay_seconds="$3"
  shift 3

  local attempt
  for attempt in $(seq 1 "${attempts}"); do
    if "$@"; then
      return 0
    fi

    log_step "Waiting for ${description} (${attempt}/${attempts})"
    sleep "${delay_seconds}"
  done

  echo "Timed out waiting for ${description} after ${attempts} attempts" >&2
  return 1
}

wait_http_ready() {
  local url="$1"
  smoke_curl --fail "${url}/readyz" >/dev/null 2>&1
}

resolve_compose_container_id() {
  local service="$1"
  docker compose ps -q "${service}" 2>/dev/null
}

compose_service_health_status() {
  local container_id="$1"
  docker inspect \
    --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' \
    "${container_id}" 2>/dev/null
}

compose_service_running() {
  local container_id="$1"
  [[ "$(docker inspect -f '{{.State.Running}}' "${container_id}" 2>/dev/null || echo false)" == "true" ]]
}

compose_health_status_is_ready() {
  local running="$1"
  local health_status="$2"
  if [[ "${running}" != "true" ]]; then
    return 1
  fi
  case "${health_status}" in
    healthy | running) return 0 ;;
    *) return 1 ;;
  esac
}

resolve_container_id() {
  local service_or_name="$1"
  local container_id=""

  container_id="$(resolve_compose_container_id "${service_or_name}")"
  if [[ -n "${container_id}" ]]; then
    printf '%s' "${container_id}"
    return 0
  fi
  if docker inspect "${service_or_name}" >/dev/null 2>&1; then
    printf '%s' "${service_or_name}"
    return 0
  fi
  return 1
}

compose_service_is_healthy() {
  local service="$1"
  local container_id health_status running

  container_id="$(resolve_container_id "${service}")" || return 1
  running="$(compose_service_running "${container_id}")"
  health_status="$(compose_service_health_status "${container_id}")"
  compose_health_status_is_ready "${running}" "${health_status}"
}

dump_compose_service_diagnostics() {
  local service="$1"
  local container_id state health

  echo "Compose service diagnostics for ${service}:" >&2
  container_id="$(resolve_container_id "${service}" 2>/dev/null || true)"
  if [[ -z "${container_id}" ]]; then
    echo "  container id: <missing>" >&2
    docker compose ps "${service}" >&2 || true
    return 1
  fi

  state="$(docker inspect -f '{{.State.Status}}' "${container_id}" 2>/dev/null || echo unknown)"
  health="$(compose_service_health_status "${container_id}")"
  echo "  container id: ${container_id}" >&2
  echo "  state: ${state}" >&2
  echo "  health: ${health}" >&2
  docker compose logs "${service}" --tail 40 --no-color 2>&1 \
    | sed -E 's/([Pp]assword|[Ss]ecret|[Tt]oken|[Aa]pi[Kk]ey)=[^[:space:]]+/\1=<redacted>/g' >&2 \
    || true
}

wait_compose_service_healthy() {
  local service="$1"
  local description="${2:-${service} healthy}"
  local attempts="${3:-30}"
  local delay_seconds="${4:-1}"

  if wait_until "${description}" "${attempts}" "${delay_seconds}" compose_service_is_healthy "${service}"; then
    return 0
  fi

  dump_compose_service_diagnostics "${service}"
  return 1
}

restart_compose_services_and_wait_healthy() {
  local description="$1"
  shift

  local service
  docker compose start "$@"
  for service in "$@"; do
    if ! wait_compose_service_healthy "${service}" "${service} healthy after restart" 30 1; then
      echo "Failed waiting for ${service} after restart (${description})" >&2
      return 1
    fi
    log_step "${service} restarted and healthy"
  done
}

# Backward-compatible helper for compose services with Docker health checks.
wait_container_healthy() {
  local service="$1"
  local label="${2:-${service}}"
  wait_compose_service_healthy "${service}" "${label} healthy" 30 1
}
