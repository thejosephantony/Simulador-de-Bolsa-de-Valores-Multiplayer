# Infra

Configurações de infraestrutura e observabilidade. O `docker-compose.yml` principal fica na raiz do repositório.

| Pasta | Conteúdo |
|---|---|
| `rabbitmq/` | `setup.sh` (DLQ via policy, executado pelo container `rabbitmq-setup`) e [README](rabbitmq/README.md) da topologia |
| `prometheus/` | `prometheus.yml`: coleta `/actuator/prometheus` de auth, market, portfolio e agents, e as métricas do RabbitMQ (`:15692`) |
| `grafana/` | provisionamento da fonte Prometheus e do dashboard "Simulador de Bolsa — Visão geral" |
| `db/` | reservado para scripts auxiliares de banco |

## Endereços (docker compose up)

| Componente | URL |
|---|---|
| Prometheus | http://localhost:9090 (alvos em Status → Targets) |
| Grafana | http://localhost:3000 (anônimo como leitor; admin/admin para editar) |
| RabbitMQ | http://localhost:15672 |

## Dashboard

Painéis: requisições/s e latência p95 por serviço, ordens no Market por status HTTP, erros 5xx, ordens enviadas e rejeitadas pelos agentes, agentes ativos, CPU e heap por serviço, conexões no PostgreSQL e mensagens no RabbitMQ.
