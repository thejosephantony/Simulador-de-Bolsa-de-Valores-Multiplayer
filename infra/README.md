# Infra

Configurações de infraestrutura e observabilidade.

Estrutura planejada:

- `rabbitmq/` — exchanges, filas e documentação de routing keys;
- `prometheus/` — configuração de coleta de métricas;
- `grafana/` — provisioning e dashboards;
- `docker/` — arquivos Docker auxiliares, quando necessários;
- `db/` — scripts auxiliares e documentação de banco.

O `docker-compose.yml` principal permanece na raiz do repositório.
