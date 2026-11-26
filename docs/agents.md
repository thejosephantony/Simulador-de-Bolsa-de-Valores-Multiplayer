# Agent Service — investidores automatizados

Serviço `backend/agent-service` (porta 8084). Os agentes **não têm acesso especial**: fazem login no Auth com conta própria (ver [seeds](seeds.md)), enviam ordens em `POST /api/v1/orders` com `Idempotency-Key`, cancelam em `DELETE /api/v1/orders/{id}` e leem a carteira em `GET /api/v1/portfolio/me`, exatamente como um humano no Home Broker. Não existe endpoint que altere saldo, carteira ou ordens por fora do Market.

## Agentes

| Nome | Conta | Estratégia | Intervalo | Qtd. máx./ordem | Ordens abertas máx. | Validade da ordem |
|---|---|---|---|---|---|---|
| `market-maker` | `agent_market_maker` | Cota compra 0,5% abaixo e venda 0,5% acima do último preço, um ativo por rodada (rodízio) | 3 s | 20 | 10 | 20 s |
| `momentum` | `agent_momentum` | Escolhe um ativo ao acaso; se o preço subiu desde a última olhada, compra no melhor preço de venda; se caiu, vende no melhor preço de compra; se não mudou, lado aleatório | 2 s | 10 | 3 | 10 s |

O market maker dá liquidez e o momentum a consome, então os dois geram trades entre si mesmo sem humanos.

## Limites aplicados em toda rodada

1. Cancela as próprias ordens abertas há mais tempo que a validade.
2. Se já tem o máximo de ordens abertas, não envia nada novo.
3. Limita a quantidade de cada ordem ao máximo configurado.
4. Antes de comprar, confere o saldo disponível; antes de vender, a quantidade disponível. Assim não manda ordem que o ledger recusaria.
5. Não cota uma ordem que cruzaria com outra ordem própria (o Market rejeita autonegociação).
6. Erros (422, rede, serviço fora) são registrados e o agente segue na próxima rodada.

## Controle para a demonstração

```bash
# estado, contadores e último erro
curl http://localhost:8084/api/v1/agents

# pausar / retomar (exige o token)
curl -X POST -H "X-Admin-Token: $AGENT_ADMIN_TOKEN" http://localhost:8084/api/v1/agents/market-maker/pause
curl -X POST -H "X-Admin-Token: $AGENT_ADMIN_TOKEN" http://localhost:8084/api/v1/agents/market-maker/resume
```

- Sem token ou com token errado: 401. Agente inexistente: 404.
- `AGENTS_START_PAUSED=true` sobe todos pausados.
- Token padrão: `change-this-agent-admin-token`; troque em `.env`.

## Métricas (Prometheus)

| Métrica | Tags |
|---|---|
| `agent_orders_submitted_total` | `agent`, `side` |
| `agent_orders_rejected_total` | `agent`, `status` |
| `agent_orders_cancelled_total` | `agent`, `reason` |
| `agent_orders_skipped_total` | `agent`, `reason` (`max_open_orders`, `insufficient_cash`, `insufficient_shares`) |
| `agent_errors_total` | `agent`, `type` |
| `agent_active` | `agent` (1 ativo, 0 pausado) |
