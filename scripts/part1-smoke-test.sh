#!/usr/bin/env bash
set -euo pipefail

AUTH_URL="${AUTH_URL:-http://localhost:8081}"
MARKET_URL="${MARKET_URL:-http://localhost:8082}"
PORTFOLIO_URL="${PORTFOLIO_URL:-http://localhost:8083}"
SMOKE_ID="${SMOKE_ID:-$(date +%s)}"

wait_for() {
  local url="$1"
  curl --fail --silent --show-error     --retry 30 --retry-delay 2 --retry-all-errors     "$url" >/dev/null
}

echo "Waiting for backend services..."
wait_for "$AUTH_URL/actuator/health"
wait_for "$MARKET_URL/actuator/health"
wait_for "$PORTFOLIO_URL/actuator/health"

echo "Registering smoke-test user..."
REGISTER_RESPONSE=$(curl --fail --silent --show-error   -X POST "$AUTH_URL/api/v1/auth/register"   -H "Content-Type: application/json"   -d "{
    \"username\": \"smoke_${SMOKE_ID}\",
    \"email\": \"smoke_${SMOKE_ID}@example.com\",
    \"password\": \"Part1Password123!\"
  }")

TOKEN=$(python3 -c 'import json,sys; print(json.load(sys.stdin)["accessToken"])' <<<"$REGISTER_RESPONSE")

echo "Checking authenticated user..."
curl --fail --silent --show-error   "$AUTH_URL/api/v1/users/me"   -H "Authorization: Bearer $TOKEN" >/dev/null

echo "Reading assets..."
ASSETS_RESPONSE=$(curl --fail --silent --show-error "$MARKET_URL/api/v1/assets")
ASSET_ID=$(python3 -c 'import json,sys; data=json.load(sys.stdin); assert data; print(data[0]["id"])' <<<"$ASSETS_RESPONSE")

echo "Reading portfolio..."
curl --fail --silent --show-error   "$PORTFOLIO_URL/api/v1/portfolio/me"   -H "Authorization: Bearer $TOKEN" >/dev/null

echo "Creating order..."
ORDER_RESPONSE=$(curl --fail --silent --show-error   -X POST "$MARKET_URL/api/v1/orders"   -H "Authorization: Bearer $TOKEN"   -H "Content-Type: application/json"   -d "{
    \"assetId\": \"$ASSET_ID\",
    \"side\": \"BUY\",
    \"price\": 25.00,
    \"quantity\": 10
  }")

ORDER_ID=$(python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])' <<<"$ORDER_RESPONSE")

echo "Checking own orders and order book..."
curl --fail --silent --show-error   "$MARKET_URL/api/v1/orders/$ORDER_ID"   -H "Authorization: Bearer $TOKEN" >/dev/null

curl --fail --silent --show-error   "$MARKET_URL/api/v1/assets/$ASSET_ID/order-book" >/dev/null

echo "Cancelling order..."
curl --fail --silent --show-error   -X DELETE "$MARKET_URL/api/v1/orders/$ORDER_ID"   -H "Authorization: Bearer $TOKEN" >/dev/null

echo "Part 1 backend smoke test passed."
