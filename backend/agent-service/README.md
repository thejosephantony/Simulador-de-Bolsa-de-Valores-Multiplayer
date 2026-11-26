# Agent Service

Investidores automatizados do mercado. Documentação completa: [docs/agents.md](../../docs/agents.md).

- Porta: 8084.
- Estratégias implementadas: `MARKET_MAKER` e `MOMENTUM` (regras determinísticas, sem ML).
- Os agentes negociam pela mesma API REST dos humanos e nunca alteram diretamente saldo, carteira ou trades.
- Configuração em `src/main/resources/application.yml` (`agents.list`).

Testes: `mvn -f backend/pom.xml -pl agent-service test`.
