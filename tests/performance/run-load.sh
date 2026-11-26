#!/usr/bin/env bash
# Executa o teste de carga k6 em 5, 20, 50 e 100 usuários e guarda os resultados.
# Requer o ambiente no ar (docker compose up -d --build). O k6 roda em container na rede do Compose.
#
# Uso: bash tests/performance/run-load.sh [níveis...]      ex.: bash tests/performance/run-load.sh 5 20
# Variáveis: COMPOSE_NETWORK (padrão: <projeto>_default), DURATION (padrão 45s), AGENT_ADMIN_TOKEN.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
if [ "$#" -gt 0 ]; then LEVELS=("$@"); else LEVELS=(5 20 50 100); fi
NETWORK="${COMPOSE_NETWORK:-$(basename "$ROOT" | tr '[:upper:]' '[:lower:]')_default}"
DURATION="${DURATION:-45s}"
RESULTS="$ROOT/tests/performance/results"
mkdir -p "$RESULTS"

for vus in "${LEVELS[@]}"; do
  echo "=== k6: $vus usuários, $DURATION (rede $NETWORK)"
  MSYS_NO_PATHCONV=1 docker run --rm --network "$NETWORK" \
    -v "$ROOT/tests/performance:/perf" \
    -e VUS="$vus" -e DURATION="$DURATION" -e RUN_ID="vus${vus}_$(date +%s)" \
    -e AUTH_URL=http://auth-service:8081 \
    -e MARKET_URL=http://market-service:8082 \
    -e PORTFOLIO_URL=http://portfolio-service:8083 \
    -e AGENTS_URL=http://agent-service:8084 \
    -e AGENT_ADMIN_TOKEN="${AGENT_ADMIN_TOKEN:-change-this-agent-admin-token}" \
    grafana/k6:0.55.0 run --quiet \
      --summary-export "/perf/results/k6-${vus}vus.json" \
      /perf/k6/trading-load.js | tee "$RESULTS/k6-${vus}vus.txt" || echo "nível $vus terminou com threshold violado (ver relatório)"
done

echo "Resultados em $RESULTS"
