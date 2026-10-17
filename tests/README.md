# Tests

Testes que atravessam componentes ou representam cenários de sistema.

- `integration/` — integração entre serviços e infraestrutura;
- `performance/` — scripts de carga com k6;
- `failure/` — roteiros/scripts de falha controlada.

Testes unitários específicos permanecem dentro de cada aplicação ou serviço.

## Testes de ordens

Os testes unitários de validação e aplicação ficam em
`backend/market-service/src/test/java/` e são executados com:

```bash
mvn -f backend/pom.xml clean verify
```

`tests/integration/test_market_orders.py` verifica o contrato HTTP, Flyway/JPA,
persistência, restrições SQL e sequências únicas sob requisições concorrentes.
A CI inicia PostgreSQL 16 e o Market Service e executa a suíte automaticamente.

Para executar manualmente, use um banco de desenvolvimento descartável, o
serviço em execução, Python 3.11+ e o cliente `psql`:

```bash
python3 tests/integration/test_market_orders.py
```

Conexão padrão: `http://localhost:8082` e PostgreSQL em `localhost:5432`, banco,
usuário e senha `stock_market`. Para outras configurações, defina
`MARKET_BASE_URL`, `PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER` e `PGPASSWORD`.
O teste usa um userId aleatório e cria um ativo inativo e ordens temporárias;
remove esses registros ao terminar, preservando o catálogo.
