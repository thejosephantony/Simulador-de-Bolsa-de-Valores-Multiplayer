#!/usr/bin/env bash
# Isolated CI environment only. Run after the trading smoke test.
set -euo pipefail
sql() { docker compose exec -T postgres sh -c 'exec psql -X -qAt -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "$1"' sh "$1"; }
# Stop only the broker application, leaving the container available for restart.
restore() { docker compose exec -T rabbitmq rabbitmqctl start_app >/dev/null 2>&1 || true; }
trap restore EXIT
wait_pending() {
  for i in $(seq 1 90); do
    if [[ "$(sql 'SELECT count(*) FROM market.outbox_events WHERE published_at IS NULL')" == 0 ]]; then return 0; fi
    sleep 2
  done
  echo 'Outbox pending events did not drain' >&2; return 1
}
wait_pending
[[ "$(sql 'SELECT count(*) FROM market.outbox_events WHERE published_at IS NOT NULL')" -gt 0 ]]
docker compose exec -T rabbitmq rabbitmqctl stop_app
# Create a real order while broker is unavailable, using a fresh test user.
AUTH_URL="${AUTH_URL:-http://localhost:8081}"
MARKET_URL="${MARKET_URL:-http://localhost:8082}"
ID="outbox_$(date +%s)_$RANDOM"
TOKEN=$(curl -fsS -X POST "$AUTH_URL/api/v1/auth/register" -H 'Content-Type: application/json' \
  -d "{\"username\":\"$ID\",\"email\":\"$ID@example.com\",\"password\":\"Part1Password123!\"}" | python3 -c 'import json,sys; print(json.load(sys.stdin)["accessToken"])')
ASSET=$(curl -fsS "$MARKET_URL/api/v1/assets" | python3 -c 'import json,sys; print(json.load(sys.stdin)[0]["id"])')
ORDER=$(curl -fsS -X POST "$MARKET_URL/api/v1/orders" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"assetId\":\"$ASSET\",\"side\":\"BUY\",\"price\":1,\"quantity\":1}" | python3 -c 'import json,sys; x=json.load(sys.stdin); assert x["status"]=="OPEN"; print(x["id"])')
# UUID is obtained from our API; ensure valid format before using it in SQL.
[[ "$ORDER" =~ ^[0-9a-fA-F-]{36}$ ]]
for i in $(seq 1 30); do
  RETRIES=$(sql "SELECT count(*) FROM market.outbox_events WHERE correlation_id = '$ORDER'::uuid AND published_at IS NULL AND attempts > 0")
  [[ "$RETRIES" -gt 0 ]] && break
  sleep 2
done
[[ "$RETRIES" -gt 0 ]]
[[ "$(sql "SELECT count(*) FROM market.outbox_events WHERE correlation_id = '$ORDER'::uuid AND published_at IS NOT NULL")" == 0 ]]
docker compose exec -T rabbitmq rabbitmqctl start_app
wait_pending
[[ "$(sql "SELECT count(*) FROM market.outbox_events WHERE correlation_id = '$ORDER'::uuid AND published_at IS NOT NULL")" -ge 3 ]]
# The queue is durable and has no consumer in this isolated workflow.
docker compose exec -T rabbitmq rabbitmqctl list_queues name messages_ready | awk '$1=="market.realtime.events" && $2>0 {found=1} END {exit !found}'
curl -fsS -X DELETE "$MARKET_URL/api/v1/orders/$ORDER" -H "Authorization: Bearer $TOKEN" >/dev/null
wait_pending
trap - EXIT
echo 'Outbox smoke passed: broker outage, persisted order, retry and recovery.'
