#!/usr/bin/env bash
# Cenários de falha controlada (R11 do plano). Rodar SOMENTE em ambiente de teste: para e reinicia containers.
# Requer o ambiente no ar (docker compose up -d --build) com os seeds (docs/seeds.md).
#
#   F1  RabbitMQ fora do ar  -> negociação continua; eventos ficam na outbox; ao voltar, a outbox drena.
#   F2  Portfolio fora do ar -> negociação continua (Market escreve o ledger); ao voltar, a carteira reflete o trade.
#   F3  Market reiniciado     -> saldo, posições, ordens e trades persistem após o restart.
#   F4  Agentes fora do ar    -> núcleo não depende deles; ao voltar, retomam pela API.
#
# Variáveis: AUTH_URL, MARKET_URL, PORTFOLIO_URL, AGENTS_URL (padrão localhost:8081..8084).
# Para um projeto Compose isolado, exporte COMPOSE_PROJECT_NAME/COMPOSE_FILE antes de rodar.
set -euo pipefail

AUTH_URL="${AUTH_URL:-http://localhost:8081}"
MARKET_URL="${MARKET_URL:-http://localhost:8082}"
PORTFOLIO_URL="${PORTFOLIO_URL:-http://localhost:8083}"
AGENTS_URL="${AGENTS_URL:-http://localhost:8084}"
AGENT_ADMIN_TOKEN="${AGENT_ADMIN_TOKEN:-change-this-agent-admin-token}"
PY="${PYTHON:-python3}"
ASSET="22222222-2222-2222-2222-222222222222" # BANK4

pass() { echo "  OK   $*"; }
fail() { echo "  FALHA $*"; exit 1; }
json() { "$PY" -c "import json,sys; d=json.load(sys.stdin); print($1)"; }
sql() { docker compose exec -T postgres sh -c 'psql -X -tA -U "$POSTGRES_USER" -d "$POSTGRES_DB"' <<<"$1" | tr -d '[:space:]'; }
login() { curl -fsS -X POST "$AUTH_URL/api/v1/auth/login" -H 'Content-Type: application/json' \
  -d "{\"identifier\":\"$1\",\"password\":\"$2\"}" | json "d['accessToken']"; }
order() { # token side price qty
  curl -sS -o /tmp/order.json -w '%{http_code}' -X POST "$MARKET_URL/api/v1/orders" \
    -H "Authorization: Bearer $1" -H 'Content-Type: application/json' \
    -H "Idempotency-Key: $("$PY" -c 'import uuid; print(uuid.uuid4())')" \
    -d "{\"assetId\":\"$ASSET\",\"side\":\"$2\",\"price\":$3,\"quantity\":$4}"; }
position() { curl -fsS "$PORTFOLIO_URL/api/v1/portfolio/me" -H "Authorization: Bearer $1" |
  json "[p['quantity'] for p in d['positions'] if p['assetId']=='$ASSET'][0]"; }
wait_http() { for _ in $(seq 1 60); do curl -fsS -o /dev/null "$1" 2>/dev/null && return 0; sleep 2; done; return 1; }
inside_spread() { # preço estritamente entre a melhor compra e a melhor venda do livro atual
  curl -fsS "$MARKET_URL/api/v1/assets/$ASSET/order-book" | "$PY" -c '
import json, sys
from decimal import Decimal
d = json.load(sys.stdin)
bid = max((Decimal(str(l["price"])) for l in d["buys"]), default=None)
ask = min((Decimal(str(l["price"])) for l in d["sells"]), default=None)
cent = Decimal("0.01")
if bid is not None and ask is not None:
    p = ((bid + ask) / 2).quantize(cent)
    p = max(p, bid + cent)
    if p >= ask: sys.exit("spread sem espaço: bid=%s ask=%s" % (bid, ask))
elif bid is not None: p = bid + cent
elif ask is not None: p = max(ask - cent, cent)
else: p = Decimal(sys.argv[1])
print(p)' "$PRICE"; }
trade() { # inv1 vende 1 dentro do spread (fica no livro); inv2 compra 1 ao mesmo preço e casa exatamente com ela
  local p; p=$(inside_spread) || fail "não foi possível escolher preço"
  [ "$(order "$T1" SELL "$p" 1)" = 201 ] || fail "venda recusada: $(cat /tmp/order.json)"
  [ "$(json "d['filledQuantity']" </tmp/order.json)" = 0 ] || fail "venda cruzou inesperadamente: $(cat /tmp/order.json)"
  [ "$(order "$T2" BUY "$p" 1)" = 201 ] || fail "compra recusada: $(cat /tmp/order.json)"
  [ "$(json "d['filledQuantity']" </tmp/order.json)" = 1 ] || fail "compra não executou: $(cat /tmp/order.json)"
}

echo "Preparando: pausa agentes e autentica as contas de demonstração"
curl -s -o /dev/null -X POST "$AGENTS_URL/api/v1/agents/market-maker/pause" -H "X-Admin-Token: $AGENT_ADMIN_TOKEN" || true
curl -s -o /dev/null -X POST "$AGENTS_URL/api/v1/agents/momentum/pause" -H "X-Admin-Token: $AGENT_ADMIN_TOKEN" || true
T1=$(login demo_investidor1 'Demo@2026!')
T2=$(login demo_investidor2 'Demo@2026!')
PRICE=$(curl -fsS "$MARKET_URL/api/v1/assets" | json "[a['currentPrice'] for a in d if a['id']=='$ASSET'][0]")

echo "F1. RabbitMQ fora do ar"
docker compose stop rabbitmq >/dev/null
trade
pass "trade executado com o broker parado"
PENDING=$(sql "select count(*) from market.outbox_events where published_at is null;")
[ "$PENDING" -gt 0 ] && pass "$PENDING evento(s) aguardando na outbox" || fail "nenhum evento pendente na outbox"
docker compose start rabbitmq >/dev/null
for _ in $(seq 1 60); do
  PENDING=$(sql "select count(*) from market.outbox_events where published_at is null;")
  [ "$PENDING" = 0 ] && break; sleep 2
done
[ "$PENDING" = 0 ] && pass "outbox drenada após o broker voltar" || fail "outbox ainda com $PENDING pendentes"

echo "F2. Portfolio fora do ar"
BEFORE=$(position "$T2")
docker compose stop portfolio-service >/dev/null
trade
pass "trade executado com o Portfolio parado"
docker compose start portfolio-service >/dev/null
wait_http "$PORTFOLIO_URL/actuator/health" || fail "Portfolio não voltou"
AFTER=$(position "$T2")
[ "$AFTER" = $((BEFORE + 1)) ] && pass "carteira reflete o trade após o restart ($BEFORE -> $AFTER)" \
  || fail "posição esperada $((BEFORE + 1)), obtida $AFTER"

echo "F3. Market reiniciado"
SNAP="select (select sum(balance) from portfolio.accounts)||'/'||(select sum(quantity) from portfolio.portfolio_positions)||'/'||(select count(*) from market.trades)||'/'||(select count(*) from market.orders);"
S1=$(sql "$SNAP")
docker compose restart market-service >/dev/null
wait_http "$MARKET_URL/actuator/health" || fail "Market não voltou"
S2=$(sql "$SNAP")
[ "$S1" = "$S2" ] && pass "estado idêntico após restart (saldo total/ações/trades/ordens = $S2)" \
  || fail "estado mudou: $S1 -> $S2"
trade
pass "negociação normal após o restart"

echo "F4. Agentes fora do ar"
docker compose stop agent-service >/dev/null
trade
pass "núcleo negocia sem os agentes"
docker compose start agent-service >/dev/null
wait_http "$AGENTS_URL/actuator/health" || fail "Agent Service não voltou"
curl -fsS -o /dev/null -X POST "$AGENTS_URL/api/v1/agents/market-maker/resume" -H "X-Admin-Token: $AGENT_ADMIN_TOKEN"
curl -fsS -o /dev/null -X POST "$AGENTS_URL/api/v1/agents/momentum/resume" -H "X-Admin-Token: $AGENT_ADMIN_TOKEN"
pass "agentes de volta e retomados"

echo "Invariantes finais"
NEG=$(sql "select count(*) from portfolio.accounts where balance<0 or reserved_balance<0 or reserved_balance>balance;")
NEGP=$(sql "select count(*) from portfolio.portfolio_positions where quantity<0 or reserved_quantity<0 or reserved_quantity>quantity;")
[ "$NEG" = 0 ] && [ "$NEGP" = 0 ] && pass "nenhum saldo ou posição negativa/inconsistente" || fail "inconsistências: contas=$NEG posições=$NEGP"
echo "Todos os cenários de falha passaram."
