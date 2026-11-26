#!/usr/bin/env bash
# Smoke da Parte 2: executar somente no ambiente de testes Compose.
# A posição inicial de B é uma fixture de teste, não uma funcionalidade de produção.
set -euo pipefail
AUTH_URL="${AUTH_URL:-http://localhost:8081}"
MARKET_URL="${MARKET_URL:-http://localhost:8082}"
PORTFOLIO_URL="${PORTFOLIO_URL:-http://localhost:8083}"
SMOKE_ID="${SMOKE_ID:-$(date +%s)_$RANDOM}"

wait_for() { curl -fsS --retry 30 --retry-delay 2 --retry-all-errors "$1" >/dev/null; }
json_value() { python3 -c 'import json,sys; print(json.load(sys.stdin)[sys.argv[1]])' "$1"; }
register_user() {
  curl -fsS -X POST "$AUTH_URL/api/v1/auth/register" -H 'Content-Type: application/json' \
    -d "{\"username\":\"smoke_${1}_${SMOKE_ID}\",\"email\":\"smoke_${1}_${SMOKE_ID}@example.com\",\"password\":\"Part1Password123!\"}"
}
portfolio() { curl -fsS "$PORTFOLIO_URL/api/v1/portfolio/me" -H "Authorization: Bearer $1"; }
order() {
  curl -fsS -X POST "$MARKET_URL/api/v1/orders" -H "Authorization: Bearer $1" -H 'Content-Type: application/json' \
    -d "{\"assetId\":\"$ASSET_ID\",\"side\":\"$2\",\"price\":$3,\"quantity\":$4}"
}
wait_for "$AUTH_URL/actuator/health"
wait_for "$MARKET_URL/actuator/health"
wait_for "$PORTFOLIO_URL/actuator/health"
register_user a >/dev/null
TOKEN_B=$(register_user b | json_value accessToken)
TOKEN_A=$(curl -fsS -X POST "$AUTH_URL/api/v1/auth/login" -H 'Content-Type: application/json' \
  -d "{\"identifier\":\"smoke_a_${SMOKE_ID}\",\"password\":\"Part1Password123!\"}" | json_value accessToken)
curl -fsS "$AUTH_URL/api/v1/users/me" -H "Authorization: Bearer $TOKEN_A" >/dev/null
curl -fsS "$AUTH_URL/api/v1/users/me" -H "Authorization: Bearer $TOKEN_B" >/dev/null
ASSET_ID=$(curl -fsS "$MARKET_URL/api/v1/assets" | python3 -c 'import json,sys; print(json.load(sys.stdin)[0]["id"])')
BEFORE_A=$(portfolio "$TOKEN_A")
BEFORE_B=$(portfolio "$TOKEN_B")
USER_B=$(json_value userId <<<"$BEFORE_B")
# Um usuário recém-criado sem posição não deve conseguir vender.
STATUS=$(curl -sS -o /dev/null -w '%{http_code}' -X POST "$MARKET_URL/api/v1/orders" \
  -H "Authorization: Bearer $TOKEN_B" -H 'Content-Type: application/json' \
  -d "{\"assetId\":\"$ASSET_ID\",\"side\":\"SELL\",\"price\":24.00,\"quantity\":7}")
[[ "$STATUS" == 422 ]]
# Fixture exclusivamente no banco Compose de teste, para o usuário criado acima.
# Não executar em produção. Usa as credenciais internas do container.
docker compose exec -T postgres sh -c 'exec psql -X -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v uid="$1" -v aid="$2"' sh "$USER_B" "$ASSET_ID" <<'SQL'
INSERT INTO portfolio.portfolio_positions
(id,user_id,asset_id,quantity,reserved_quantity,average_price,version)
VALUES(gen_random_uuid(), :'uid'::uuid, :'aid'::uuid,20,0,20,0);
SQL
KEY="smoke_${SMOKE_ID}_buy"
keyed_buy() {
  curl -fsS -X POST "$MARKET_URL/api/v1/orders" -H "Authorization: Bearer $TOKEN_A" \
    -H 'Content-Type: application/json' -H "Idempotency-Key: $KEY" \
    -d "{\"assetId\":\"$ASSET_ID\",\"side\":\"BUY\",\"price\":24.50,\"quantity\":10}"
}
BUY=$(keyed_buy)
BUY_ID=$(json_value id <<<"$BUY")
[[ $(json_value status <<<"$BUY") == OPEN ]]
[[ $(keyed_buy | json_value id) == "$BUY_ID" ]]
STATUS=$(curl -sS -o /dev/null -w '%{http_code}' -X POST "$MARKET_URL/api/v1/orders" \
  -H "Authorization: Bearer $TOKEN_A" -H 'Content-Type: application/json' -H "Idempotency-Key: $KEY" \
  -d "{\"assetId\":\"$ASSET_ID\",\"side\":\"BUY\",\"price\":24.50,\"quantity\":11}")
[[ "$STATUS" == 409 ]]
SELL=$(order "$TOKEN_B" SELL 24.00 7)
[[ $(json_value status <<<"$SELL") == FILLED ]]
[[ $(json_value filledQuantity <<<"$SELL") == 7 ]]
BUY_AFTER=$(curl -fsS "$MARKET_URL/api/v1/orders/$BUY_ID" -H "Authorization: Bearer $TOKEN_A")
[[ $(json_value status <<<"$BUY_AFTER") == PARTIALLY_FILLED ]]
[[ $(json_value remainingQuantity <<<"$BUY_AFTER") == 3 ]]
# Histórico público sem identidade e histórico privado vinculado ao token.
PUBLIC=$(curl -fsS "$MARKET_URL/api/v1/assets/$ASSET_ID/trades")
MY_A=$(curl -fsS "$MARKET_URL/api/v1/trades/me" -H "Authorization: Bearer $TOKEN_A")
MY_B=$(curl -fsS "$MARKET_URL/api/v1/trades/me" -H "Authorization: Bearer $TOKEN_B")
python3 - "$PUBLIC" "$MY_A" "$MY_B" "$BUY_ID" <<'PYTEST'
import json,sys
p,a,b=map(json.loads,sys.argv[1:4])
assert len(a)==len(b)==1 and a[0]['id']==b[0]['id']
assert a[0]['side']=='BUY' and b[0]['side']=='SELL' and a[0]['orderId']==sys.argv[4]
assert a[0]['quantity']==b[0]['quantity']==7
assert any(t['id']==a[0]['id'] for t in p)
assert all(set(t)=={'id','assetId','price','quantity','executedAt'} for t in p)
PYTEST
STATUS=$(curl -sS -o /dev/null -w '%{http_code}' "$MARKET_URL/api/v1/trades/me")
[[ "$STATUS" == 401 ]]
# Outra oferta não cruza, de modo que o livro permanece com compras e vendas.
SELL2=$(order "$TOKEN_B" SELL 25.50 2)
SELL2_ID=$(json_value id <<<"$SELL2")
BOOK=$(curl -fsS "$MARKET_URL/api/v1/assets/$ASSET_ID/order-book")
python3 -c 'import json,sys; d=json.load(sys.stdin); assert d["buys"] and d["sells"]' <<<"$BOOK"
# Indicators use remaining book quantities and the last trade price.
ASSETS=$(curl -fsS "$MARKET_URL/api/v1/assets")
python3 - "$ASSETS" "$ASSET_ID" <<'PYIND'
import json,sys
from decimal import Decimal as D
a=next(x for x in json.loads(sys.argv[1],parse_float=D) if x['id']==sys.argv[2])
assert D(a['currentPrice'])==D('24.50') and D(a['tradedVolume'])>=7
assert D(a['bestBid'])==D('24.50') and D(a['bestAsk'])==D('25.50')
expected=((D(a['currentPrice'])-D(a['initialPrice']))/D(a['initialPrice'])*100).quantize(D('0.0001'))
assert D(a['priceChangePercent'])==expected
PYIND
# The idempotent BUY produced one initial private order event and one subsequent update.
# It did not create another initial event on replay.
docker compose exec -T postgres sh -c 'exec psql -X -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v oid="$1" -v aid="$2"' sh "$BUY_ID" "$ASSET_ID" <<'SQLOUT'
SELECT 1 / CASE WHEN count(*) = 1 THEN 1 ELSE 0 END FROM market.outbox_events
WHERE correlation_id = :'oid'::uuid AND event_type = 'order.changed' AND payload->>'orderId' = :'oid';
SELECT 1 / CASE WHEN count(*) >= 1 THEN 1 ELSE 0 END FROM market.outbox_events
WHERE asset_id = :'aid'::uuid AND event_type = 'trade.executed' AND user_id IS NULL;
SQLOUT
ORDERS_A=$(curl -fsS "$MARKET_URL/api/v1/orders" -H "Authorization: Bearer $TOKEN_A")
ORDERS_B=$(curl -fsS "$MARKET_URL/api/v1/orders" -H "Authorization: Bearer $TOKEN_B")
python3 -c 'import json,sys; a,b=map(json.loads,sys.argv[1:]); assert len(a)==1 and a[0]["side"]=="BUY"; assert len(b)==2 and all(o["side"]=="SELL" for o in b)' "$ORDERS_A" "$ORDERS_B"
AFTER_A=$(portfolio "$TOKEN_A")
AFTER_B=$(portfolio "$TOKEN_B")
python3 - "$BEFORE_A" "$BEFORE_B" "$AFTER_A" "$AFTER_B" "$ASSET_ID" <<'PY'
import json,sys
from decimal import Decimal as D
ba,bb,a,b=[json.loads(x,parse_float=D) for x in sys.argv[1:5]]
asset=sys.argv[5]
assert D(a['balance'])==D(ba['balance'])-D('171.50')
assert D(b['balance'])==D(bb['balance'])+D('171.50')
assert D(a['reservedBalance'])==D('73.50')
pa=next(p for p in a['positions'] if p['assetId']==asset)
pb=next(p for p in b['positions'] if p['assetId']==asset)
assert pa['quantity']==7 and D(pa['averagePrice'])==D('24.50')
assert pb['quantity']==13 and pb['reservedQuantity']==2
PY
curl -fsS -X DELETE "$MARKET_URL/api/v1/orders/$BUY_ID" -H "Authorization: Bearer $TOKEN_A" >/dev/null
curl -fsS -X DELETE "$MARKET_URL/api/v1/orders/$SELL2_ID" -H "Authorization: Bearer $TOKEN_B" >/dev/null
portfolio "$TOKEN_A" | python3 -c 'import json,sys; assert json.load(sys.stdin)["reservedBalance"]==0'
portfolio "$TOKEN_B" | python3 -c 'import json,sys; assert all(p["reservedQuantity"]==0 for p in json.load(sys.stdin)["positions"])'
echo 'Backend smoke test passed: matching, partial fill, settlement, reservation release and two clients.'
