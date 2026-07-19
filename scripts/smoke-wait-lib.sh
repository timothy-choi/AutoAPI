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

assert_container_still_running() {
  local container_name="$1"
  local running exit_code

  if ! docker inspect "${container_name}" >/dev/null 2>&1; then
    echo "Container ${container_name} does not exist" >&2
    return 1
  fi

  running="$(docker inspect --format '{{.State.Running}}' "${container_name}" 2>/dev/null || echo false)"
  if [[ "${running}" == "true" ]]; then
    return 0
  fi

  exit_code="$(docker inspect --format '{{.State.ExitCode}}' "${container_name}" 2>/dev/null || echo 1)"
  echo "Container ${container_name} exited before readiness (exit=${exit_code})" >&2
  if declare -F smoke_redact_container_env >/dev/null; then
    docker logs "${container_name}" --tail 100 2>&1 | smoke_redact_container_env >&2 || true
  else
    docker logs "${container_name}" --tail 100 >&2 || true
  fi
  return 1
}

wait_http_ready_for_container() {
  local url="$1"
  local container_name="${2:-}"

  if [[ -n "${container_name}" ]] && ! assert_container_still_running "${container_name}"; then
    return 1
  fi

  wait_http_ready "${url}"
}

resolve_compose_container_id() {
  local service="$1"
  docker compose ps -q "${service}" 2>/dev/null | head -n1 | tr -d '\r\n[:space:]'
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

compose_service_inspect_status() {
  local container_id="$1"
  docker inspect \
    --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' \
    "${container_id}" \
    2>/dev/null |
    tr -d '\r\n[:space:]'
}

compose_readiness_status_is_ready() {
  local status="$1"
  status="$(printf '%s' "${status}" | tr -d '\r\n[:space:]')"
  case "${status}" in
    healthy) return 0 ;;
    running)
      # Containers without a Docker health check expose State.Status=running when up.
      return 0
      ;;
    *) return 1 ;;
  esac
}

# Returns observed status on stdout; exit 0 when the status string was read.
compose_service_readiness_status() {
  local service_name="$1"
  local container_id status

  container_id="$(resolve_container_id "${service_name}" 2>/dev/null || true)"
  if [[ -z "${container_id}" ]]; then
    printf 'missing'
    return 1
  fi

  status="$(compose_service_inspect_status "${container_id}")"
  if [[ -z "${status}" ]]; then
    printf 'unknown'
    return 1
  fi

  printf '%s' "${status}"
  return 0
}

compose_service_observed_status() {
  local service="$1"
  local status=""

  status="$(compose_service_readiness_status "${service}" 2>/dev/null || true)"
  if [[ -z "${status}" ]]; then
    printf 'missing'
    return 1
  fi
  printf '%s' "${status}"
  return 0
}

compose_service_is_healthy() {
  local service_name="$1"
  local status

  if ! status="$(compose_service_readiness_status "${service_name}")"; then
    return 1
  fi

  if compose_readiness_status_is_ready "${status}"; then
    return 0
  fi

  return 1
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
  health="$(compose_service_inspect_status "${container_id}")"
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
  local attempt observed

  for attempt in $(seq 1 "${attempts}"); do
    if compose_service_is_healthy "${service}"; then
      return 0
    fi
    observed="$(compose_service_observed_status "${service}")"
    log_step "Waiting for ${description} (${attempt}/${attempts}, observed=${observed})"
    sleep "${delay_seconds}"
  done

  echo "Timed out waiting for ${description} after ${attempts} attempts" >&2
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

# Backward-compatible helper for compose services and named docker-run containers.
wait_container_healthy() {
  local service="$1"
  local label="${2:-${service}}"
  if wait_compose_service_healthy "${service}" "${label} healthy" 30 1; then
    log_step "${label} healthy"
    return 0
  fi
  return 1
}
