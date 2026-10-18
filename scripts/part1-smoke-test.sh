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

json_value() {
  local key="$1"
  python3 -c "import json,sys; print(json.load(sys.stdin)['$key'])"
}

register_user() {
  local suffix="$1"
  curl --fail --silent --show-error     -X POST "$AUTH_URL/api/v1/auth/register"     -H "Content-Type: application/json"     -d "{
      \"username\": \"smoke_${suffix}_${SMOKE_ID}\",
      \"email\": \"smoke_${suffix}_${SMOKE_ID}@example.com\",
      \"password\": \"Part1Password123!\"
    }"
}

login_user() {
  local suffix="$1"
  curl --fail --silent --show-error     -X POST "$AUTH_URL/api/v1/auth/login"     -H "Content-Type: application/json"     -d "{
      \"identifier\": \"smoke_${suffix}_${SMOKE_ID}\",
      \"password\": \"Part1Password123!\"
    }"
}

echo "Waiting for backend services..."
wait_for "$AUTH_URL/actuator/health"
wait_for "$MARKET_URL/actuator/health"
wait_for "$PORTFOLIO_URL/actuator/health"

echo "Registering two clients..."
register_user "a" >/dev/null
REGISTER_B=$(register_user "b")
TOKEN_B=$(json_value "accessToken" <<<"$REGISTER_B")

echo "Authenticating client A through login..."
LOGIN_A=$(login_user "a")
TOKEN_A=$(json_value "accessToken" <<<"$LOGIN_A")

echo "Checking authenticated identities..."
curl --fail --silent --show-error   "$AUTH_URL/api/v1/users/me"   -H "Authorization: Bearer $TOKEN_A" >/dev/null

curl --fail --silent --show-error   "$AUTH_URL/api/v1/users/me"   -H "Authorization: Bearer $TOKEN_B" >/dev/null

echo "Reading shared asset catalog..."
ASSETS_RESPONSE=$(curl --fail --silent --show-error "$MARKET_URL/api/v1/assets")
ASSET_ID=$(python3 -c 'import json,sys; data=json.load(sys.stdin); assert data; print(data[0]["id"])' <<<"$ASSETS_RESPONSE")

echo "Reading both portfolios..."
curl --fail --silent --show-error   "$PORTFOLIO_URL/api/v1/portfolio/me"   -H "Authorization: Bearer $TOKEN_A" >/dev/null

curl --fail --silent --show-error   "$PORTFOLIO_URL/api/v1/portfolio/me"   -H "Authorization: Bearer $TOKEN_B" >/dev/null

echo "Client A creates BUY order..."
BUY_RESPONSE=$(curl --fail --silent --show-error   -X POST "$MARKET_URL/api/v1/orders"   -H "Authorization: Bearer $TOKEN_A"   -H "Content-Type: application/json"   -d "{
    \"assetId\": \"$ASSET_ID\",
    \"side\": \"BUY\",
    \"price\": 24.50,
    \"quantity\": 10
  }")
BUY_ID=$(json_value "id" <<<"$BUY_RESPONSE")

echo "Client B creates SELL order..."
SELL_RESPONSE=$(curl --fail --silent --show-error   -X POST "$MARKET_URL/api/v1/orders"   -H "Authorization: Bearer $TOKEN_B"   -H "Content-Type: application/json"   -d "{
    \"assetId\": \"$ASSET_ID\",
    \"side\": \"SELL\",
    \"price\": 25.50,
    \"quantity\": 7
  }")
SELL_ID=$(json_value "id" <<<"$SELL_RESPONSE")

echo "Checking per-user order isolation..."
ORDERS_A=$(curl --fail --silent --show-error   "$MARKET_URL/api/v1/orders"   -H "Authorization: Bearer $TOKEN_A")
python3 -c 'import json,sys; data=json.load(sys.stdin); assert len(data) == 1 and data[0]["side"] == "BUY"' <<<"$ORDERS_A"

ORDERS_B=$(curl --fail --silent --show-error   "$MARKET_URL/api/v1/orders"   -H "Authorization: Bearer $TOKEN_B")
python3 -c 'import json,sys; data=json.load(sys.stdin); assert len(data) == 1 and data[0]["side"] == "SELL"' <<<"$ORDERS_B"

echo "Checking shared order book..."
BOOK=$(curl --fail --silent --show-error "$MARKET_URL/api/v1/assets/$ASSET_ID/order-book")
python3 -c 'import json,sys; data=json.load(sys.stdin); assert data["buys"] and data["sells"]' <<<"$BOOK"

echo "Cancelling both orders..."
curl --fail --silent --show-error   -X DELETE "$MARKET_URL/api/v1/orders/$BUY_ID"   -H "Authorization: Bearer $TOKEN_A" >/dev/null

curl --fail --silent --show-error   -X DELETE "$MARKET_URL/api/v1/orders/$SELL_ID"   -H "Authorization: Bearer $TOKEN_B" >/dev/null

echo "Part 1 backend smoke test passed for two clients."
