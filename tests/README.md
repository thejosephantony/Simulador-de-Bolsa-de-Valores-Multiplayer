# Tests

Testes que atravessam componentes ou representam cenários de sistema.

- `integration/` — integração entre serviços e infraestrutura;
- `performance/` — carga com k6: `bash tests/performance/run-load.sh` (5, 20, 50 e 100 usuários; resultados em `performance/results/`);
- `failure/` — falha controlada: `bash tests/failure/failure-scenarios.sh` (RabbitMQ, Portfolio, Market e agentes). **Para e reinicia containers; usar só em ambiente de teste.**

Resultados e análise: [docs/relatorio-carga-falhas.md](../docs/relatorio-carga-falhas.md).

Testes unitários específicos permanecem dentro de cada aplicação ou serviço.
