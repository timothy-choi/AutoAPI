#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT}"

CONTROL_PLANE_URL="${CONTROL_PLANE_URL:-http://localhost:8081}"
SMOKE_SKIP_UP="${SMOKE_SKIP_UP:-false}"
WEBHOOK_PORT="${SMOKE_WEBHOOK_PORT:-9099}"
WEBHOOK_PID=""

# shellcheck source=scripts/smoke-curl-lib.sh
source "${ROOT}/scripts/smoke-curl-lib.sh"
# shellcheck source=scripts/smoke-compose-lib.sh
source "${ROOT}/scripts/smoke-compose-lib.sh"
# shellcheck source=scripts/smoke-wait-lib.sh
source "${ROOT}/scripts/smoke-wait-lib.sh"

cleanup() {
  if [[ -n "${WEBHOOK_PID}" ]]; then
    kill "${WEBHOOK_PID}" >/dev/null 2>&1 || true
  fi
  if [[ "${SMOKE_SKIP_UP:-false}" != "true" ]]; then
    timeout 120 docker compose down -v >/dev/null 2>&1 || true
  fi
}

trap cleanup EXIT

start_webhook_receiver() {
  python3 - "${WEBHOOK_PORT}" <<'PY' &
import json, hmac, hashlib, sys, time
from http.server import BaseHTTPRequestHandler, HTTPServer

PORT = int(sys.argv[1])
STATE = {"status": 200, "deliveries": []}

class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path == "/state":
            body = json.dumps(STATE).encode()
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
            return
        if self.path.startswith("/config/status/"):
            STATE["status"] = int(self.path.split("/")[-1])
            self.send_response(204)
            self.end_headers()
            return
        self.send_response(404)
        self.end_headers()

    def do_POST(self):
        length = int(self.headers.get("Content-Length", "0"))
        body = self.rfile.read(length)
        STATE["deliveries"].append({
            "headers": {k: self.headers[k] for k in self.headers},
            "body": body.decode("utf-8"),
        })
        self.send_response(STATE["status"])
        self.end_headers()

    def log_message(self, fmt, *args):
        return

HTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
PY
  WEBHOOK_PID=$!
  wait_for_http "${CONTROL_PLANE_URL}/actuator/health" "control-plane health" 120 || true
  for _ in $(seq 1 30); do
    if curl -sf "http://127.0.0.1:${WEBHOOK_PORT}/state" >/dev/null 2>&1; then
      return 0
    fi
    sleep 0.2
  done
  echo "Webhook receiver failed to start" >&2
  exit 1
}

json_field() {
  python3 - "$1" "$2" <<'PY'
import json, sys
payload = json.loads(sys.argv[1])
print(payload[sys.argv[2]])
PY
}

if [[ "${SMOKE_SKIP_UP}" != "true" ]]; then
  set_smoke_step "Starting docker compose stack"
  docker compose up -d --build
  wait_for_http "${CONTROL_PLANE_URL}/actuator/health" "control-plane health" 180
fi

set_smoke_step "Starting webhook receiver"
start_webhook_receiver

set_smoke_step "Creating project"
PROJECT_JSON="$(smoke_curl -sf -X POST "${CONTROL_PLANE_URL}/api/v1/projects" \
  -H 'Content-Type: application/json' \
  -d '{"name":"phase11-smoke","description":"phase 11"}')"
PROJECT_ID="$(json_field "${PROJECT_JSON}" id)"

set_smoke_step "Creating webhook subscription"
WEBHOOK_JSON="$(smoke_curl -sf -X POST \
  "${CONTROL_PLANE_URL}/api/v1/management/projects/${PROJECT_ID}/webhooks" \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"smoke-hook\",\"url\":\"http://127.0.0.1:${WEBHOOK_PORT}/hook\",\"eventFilters\":[\"webhook.test.v1\"]}")"
WEBHOOK_ID="$(python3 - "${WEBHOOK_JSON}" <<'PY'
import json, sys
print(json.loads(sys.argv[1])["subscription"]["id"])
PY
)"
SECRET="$(python3 - "${WEBHOOK_JSON}" <<'PY'
import json, sys
print(json.loads(sys.argv[1])["secret"])
PY
)"

set_smoke_step "Triggering test webhook delivery"
smoke_curl -sf -X POST \
  "${CONTROL_PLANE_URL}/api/v1/management/projects/${PROJECT_ID}/webhooks/${WEBHOOK_ID}/test" \
  -H 'Content-Type: application/json' -d '{}' >/dev/null

set_smoke_step "Waiting for webhook delivery"
for _ in $(seq 1 60); do
  COUNT="$(curl -sf "http://127.0.0.1:${WEBHOOK_PORT}/state" | python3 -c 'import json,sys; print(len(json.load(sys.stdin)["deliveries"]))')"
  if [[ "${COUNT}" -ge 1 ]]; then
    break
  fi
  sleep 0.5
done
if [[ "${COUNT:-0}" -lt 1 ]]; then
  echo "Expected webhook delivery" >&2
  exit 1
fi

set_smoke_step "Verifying event API"
EVENTS="$(smoke_curl -sf "${CONTROL_PLANE_URL}/api/v1/management/events?projectId=${PROJECT_ID}&eventType=project.created.v1")"
python3 - "${EVENTS}" <<'PY'
import json, sys
events = json.loads(sys.argv[1])
assert len(events) >= 1, events
PY

log_step "Phase 11 smoke completed successfully"
