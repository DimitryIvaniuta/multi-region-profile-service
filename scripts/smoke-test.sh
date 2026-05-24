#!/usr/bin/env bash
set -euo pipefail

PRIMARY_URL="${PRIMARY_URL:-http://localhost:8080}"
READ_URL="${READ_URL:-http://localhost:8081}"
API_KEY="${API_KEY:-local-api-key}"
INTERNAL_KEY="${INTERNAL_KEY:-local-internal-key}"
EMAIL="smoke.$(date +%s)@example.com"

create_response="$({
  curl -fsS -X POST "$PRIMARY_URL/api/v1/profiles" \
    -H "Content-Type: application/json" \
    -H "X-API-Key: $API_KEY" \
    -H "X-Correlation-Id: smoke-create" \
    -d "{\"email\":\"$EMAIL\",\"displayName\":\"Smoke Test\",\"phone\":\"+48100100200\"}"
})"

profile_id="$(python -c 'import json,sys; print(json.load(sys.stdin)["profileId"])' <<<"$create_response")"
version="$(python -c 'import json,sys; print(json.load(sys.stdin)["version"])' <<<"$create_response")"

for attempt in {1..20}; do
  if curl -fsS "$READ_URL/api/v1/profiles/$profile_id?minVersion=$version" \
      -H "X-API-Key: $API_KEY" \
      -H "X-Correlation-Id: smoke-read" >/tmp/profile-smoke-read.json; then
    cat /tmp/profile-smoke-read.json
    printf '\nSmoke test passed for profileId=%s version=%s\n' "$profile_id" "$version"
    curl -fsS "$READ_URL/internal/replication/lag" -H "X-Internal-Key: $INTERNAL_KEY" >/tmp/profile-smoke-lag.json || true
    cat /tmp/profile-smoke-lag.json || true
    printf '\n'
    exit 0
  fi
  sleep 1
done

printf 'Profile did not become visible in read region within smoke-test timeout. profileId=%s version=%s\n' "$profile_id" "$version" >&2
exit 1
