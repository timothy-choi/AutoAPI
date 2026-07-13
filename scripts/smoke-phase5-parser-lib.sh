#!/usr/bin/env bash
# Shared upstream-health JSON parser for smoke-phase5.sh and parser self-tests.
# Exit codes: 0 = target found, 2 = target not found, 1 = JSON parse error.

parse_target_health() {
  local target_id="$1"
  python3 -c '
import json
import sys

target_id = sys.argv[1]
try:
    data = json.load(sys.stdin)
except json.JSONDecodeError as exc:
    print(f"health JSON parse error: {exc}", file=sys.stderr)
    raise SystemExit(1)

for pool in data.get("pools", []):
    for target in pool.get("targets", []):
        if target.get("targetId") == target_id:
            ejected_until = target.get("ejectedUntil")
            last_category = target.get("lastFailureCategory")
            values = [
                str(target.get("state") or ""),
                str(target.get("consecutiveFailures", 0)),
                "" if ejected_until is None else str(ejected_until),
                "" if last_category is None else str(last_category),
            ]
            print("\t".join(values))
            raise SystemExit(0)

raise SystemExit(2)
' "${target_id}"
}

read_parsed_target_health() {
  local health_json="$1"
  local target_id="$2"
  local parsed=""
  local parse_status=0

  set +e
  parsed="$(printf '%s' "${health_json}" | parse_target_health "${target_id}")"
  parse_status=$?
  set -e

  if [[ ${parse_status} -eq 1 ]]; then
    echo "Failed to parse upstream health JSON for target ${target_id}" >&2
    printf '%s\n' "${health_json}" >&2
    return 1
  fi
  if [[ ${parse_status} -eq 2 ]]; then
    echo "upstream-v1 target ${target_id} not found in health response" >&2
    printf '%s\n' "${health_json}" >&2
    return 1
  fi

  printf '%s' "${parsed}"
  return 0
}
