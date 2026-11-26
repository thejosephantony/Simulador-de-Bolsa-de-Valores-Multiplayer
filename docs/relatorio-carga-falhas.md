# Relatório de carga e falhas — Parte 2

Execução real em 26/11/2026. Testes R11 (falha), R12/R13 (carga e métricas) e R15 (Compose) do plano.

## Ambiente

- Uma máquina: Windows 11 com Docker Desktop (16 CPUs e 7,6 GB de RAM disponíveis para o Docker).
- Compose com todos os serviços em um único host: PostgreSQL 16, RabbitMQ 3, Auth, Market, Portfolio, Agent, Prometheus e Grafana. Banco novo, com os seeds de [docs/seeds.md](seeds.md).
- k6 0.55 em container na mesma rede do Compose. A latência medida não inclui rede externa.

## R12/R13 — Carga

Script: `tests/performance/k6/trading-load.js`. Execução: `bash tests/performance/run-load.sh`. Saídas brutas: `tests/performance/results/k6-<N>vus.{txt,json}`.

Cada usuário virtual repete, com pausa de 0,5 s: consulta ativos, consulta o livro, envia 1 ordem e consulta a carteira.
- Metade dos usuários compra a +1% do último preço, com contas novas.
- A outra metade vende a −1% pela conta semeada `agent_market_maker`. Isso força **disputa pela mesma conta** no ledger.
- Os preços se cruzam, então há trades reais.
- Os agentes ficam pausados durante a medição.
- Duração: 45 s por nível.

| Usuários | Requisições/s | Ordens/s | Ordens que geraram trade/s | Falhas HTTP | Ordem p50 | Ordem p95 | Ordem p99 | Ordem máx. | p95 geral |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 5 | 38,4 | 9,5 | 4,4 | 0% | 10 ms | 23 ms | 30 ms | 46 ms | 15 ms |
| 20 | 150,3 | 37,3 | 18,3 | 0% | 10 ms | 34 ms | 51 ms | 83 ms | 17 ms |
| 50 | 361,9 | 90,2 | 44,3 | 0% | 13 ms | 58 ms | 96 ms | 168 ms | 30 ms |
| 100 | 697,1 | 173,8 | 85,7 | 0% | 12 ms | 63 ms | 110 ms | 300 ms | 30 ms |

"Ordens que geraram trade/s" conta as ordens que executaram ao chegar (`filledQuantity > 0`). Cada uma gerou pelo menos um trade de 1 ação. No total foram 8.523 ordens aceitas e 4.202 execuções a 100 usuários, todas com HTTP 201.

### Recursos no pico (Prometheus, durante os 4 níveis)

| Serviço | CPU máx. (fração da máquina de 16 CPUs) | Heap JVM máx. | Conexões PostgreSQL ativas máx. (pool = 10) |
|---|---:|---:|---:|
| market-service | 6,5% (≈ 1 núcleo) | 174 MB | **10** |
| portfolio-service | 3,3% | 90 MB | 2 |
| auth-service | 3,4% | 84 MB | 1 |
| agent-service | 1,2% | 47 MB | — |

### Análise

- **A vazão cresce quase linearmente**, de 9,5 para 173,8 ordens/s, sem erros. O sistema acompanhou a carga oferecida em todos os níveis.
- **A latência mediana não muda** (10–13 ms); a cauda é que cresce (p99 de 30 para 110 ms). É o efeito esperado da serialização por ativo: ordens do mesmo ativo esperam o bloqueio `FOR UPDATE` da linha do ativo. E todos os vendedores disputam a mesma conta.
- **O primeiro gargalo é o pool de conexões do Market**, que chegou a 10/10 com 100 usuários. A CPU continua baixa (≈ 1 núcleo), então o limite atual é espera por bloqueio e conexão, não processamento. Próximos passos, se for preciso escalar:
  - aumentar `spring.datasource.hikari.maximum-pool-size` do Market;
  - separar a conexão usada pelo publisher da outbox, que segura uma conexão enquanto espera o ACK do broker.
- **Limitações**: tudo roda numa máquina só, incluindo o gerador de carga; são 45 s por nível; e há 5 ativos, o que concentra a disputa. Os números servem para comparar níveis, não como capacidade de produção.

### Observação sobre a primeira execução

Na primeira rodada, o nível de 5 usuários teve 97 respostas HTTP 409 (5,7%). A causa estava no próprio teste: o agente market maker, pausado, ainda tinha compras abertas na mesma conta usada pelos vendedores do k6, e o Market recusou corretamente a autonegociação. O script passou a cancelar essas ordens no `setup`. A tabela acima é da segunda rodada, completa, com o teste corrigido.

## R11 — Falhas controladas

Script: `tests/failure/failure-scenarios.sh`. Executado duas vezes seguidas; as duas passaram.

| Cenário | O que foi feito | Resultado |
|---|---|---|
| F1. RabbitMQ fora | `docker compose stop rabbitmq`, trade entre duas contas, `start` | Trade executado com o broker parado; 18 e 9 eventos ficaram na outbox (rodadas 1 e 2); a outbox **drenou** após o broker voltar |
| F2. Portfolio fora | `stop portfolio-service`, trade, `start` | Trade executado (o Market escreve o ledger na própria transação); depois do restart, a carteira mostrou a nova posição (205 → 206, depois 209 → 210) |
| F3. Market reiniciado | `restart market-service` | Saldo total, total de ações, trades e ordens idênticos antes e depois; negociação normal em seguida |
| F4. Agentes fora | `stop agent-service`, trade, `start`, retomar | O núcleo negociou sem os agentes; eles voltaram e foram retomados pela API |
| Invariantes | Consulta no banco ao final | Nenhum saldo, reserva ou posição negativa ou inconsistente |

**Conservação:** em todas as rodadas, o saldo total do sistema ficou em **1.400.000,00** (a soma das quatro contas semeadas) e o total de ações em **29.500** (5.900 × 5 ativos). Trades, falhas e restarts não criaram nem destruíram dinheiro ou ações.

O cenário "Realtime fora do ar" depende do Realtime Service (Frente 02) e não foi coberto aqui.

## Outros testes executados

| Teste | Resultado |
|---|---|
| `mvn clean verify` (5 módulos) | BUILD SUCCESS. 45 testes: 17 do ledger em PostgreSQL real, 24 do Market e 4 das estratégias dos agentes |
| Scripts do CI do Market num Compose limpo (`part1-smoke-test.sh`, `market-concurrency-test.py`, `outbox-smoke-test.sh`) | Os três passaram com seeds, DLQ e métricas ativos |
| DLQ | Mensagem rejeitada sem requeue chegou a `market.realtime.events.dlq` com `x-death` (`reason=rejected`) |
| Seeds | Logins das 4 contas; trade real entre `demo_investidor1` e `demo_investidor2` a preço passivo; venda a descoberto recusada (422) |
| Agentes | Negociaram entre si pela API (trades reais); pausar e retomar com token; 401 sem token ou com token errado; 404 para agente inexistente |
| Observabilidade | 5 alvos `up` no Prometheus; dashboard e fonte de dados provisionados no Grafana; `/actuator/prometheus` liberado e as APIs de negócio continuam exigindo JWT (401) |
| Compose (R15) | Todos os serviços sobem com health check verde, a partir de imagens recém-construídas |
