#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT}"

# Lifecycle modes:
# - Self-managed (default): start Compose, wait for readiness, run assertions, cleanup on exit.
# - Externally managed (SMOKE_SKIP_UP=true): assume Compose is already running; never cleanup.
started_stack=false

cleanup() {
  if [[ "${started_stack}" == "true" ]]; then
    docker compose down -v || true
  fi
}

trap cleanup EXIT

if [[ "${SMOKE_SKIP_UP:-false}" != "true" ]]; then
  started_stack=true
  docker compose up --build -d

  ready=false
  for _ in $(seq 1 30); do
    if curl --fail --silent http://localhost:8080/readyz >/dev/null; then
      ready=true
      break
    fi
    sleep 2
  done

  if [[ "${ready}" != "true" ]]; then
    echo "Gateway did not become ready" >&2
    docker compose logs
    exit 1
  fi
fi

echo "== Proxy success =="
response="$(curl -si -H 'Host: api.autoapi.local' http://localhost:8080/v1/orders/123)"
echo "${response}" | head -n 20
echo "${response}" | grep -i '^X-Request-ID:' >/dev/null

echo "== Method not allowed =="
curl -si -X DELETE -H 'Host: api.autoapi.local' http://localhost:8080/v1/orders/123 | tee /tmp/autoapi-405.txt
grep '405' /tmp/autoapi-405.txt >/dev/null
grep -i '^Allow:' /tmp/autoapi-405.txt >/dev/null
grep 'METHOD_NOT_ALLOWED' /tmp/autoapi-405.txt >/dev/null

echo "== Route not found =="
curl -si -H 'Host: api.autoapi.local' http://localhost:8080/unknown | tee /tmp/autoapi-404.txt
grep '404' /tmp/autoapi-404.txt >/dev/null
grep 'ROUTE_NOT_FOUND' /tmp/autoapi-404.txt >/dev/null

echo "Smoke tests passed"
