# Estrutura do repositório

O projeto utiliza um **monorepo** organizado por áreas técnicas. A estrutura deixa explícita a separação entre backend, frontend, infraestrutura, testes e documentação, sem alterar as fronteiras da arquitetura distribuída.

## Estrutura

```text
.
├── backend/
│   ├── pom.xml
│   ├── auth-service/
│   ├── market-service/
│   ├── portfolio-service/
│   ├── realtime-service/
│   ├── agent-service/
│   ├── gateway/
│   └── ai-service/
├── frontend/
├── infra/
│   ├── db/
│   ├── rabbitmq/
│   ├── prometheus/
│   └── grafana/
├── tests/
│   ├── integration/
│   ├── performance/
│   └── failure/
├── docs/
│   ├── architecture/
│   ├── adr/
│   ├── api/
│   ├── events/
│   └── diagrams/
├── scripts/
├── .github/
├── docker-compose.yml
├── .env.example
└── README.md
```

## Fronteiras arquiteturais

- **Auth Service**: identidade, autenticação e autorização.
- **Market Service**: ativos, ordens, Order Book, Matching Engine e trades.
- **Portfolio Service**: saldo, reservas, posições e patrimônio.
- **Realtime Service**: WebSocket/STOMP e sincronização dos clientes.
- **Agent Service**: investidores automatizados.
- **Gateway**: entrada e roteamento quando sua introdução for necessária.
- **AI Service**: ML opcional; não altera diretamente saldo, carteira ou trades.

O fato de todos os serviços estarem em `backend/` é apenas uma organização do monorepo. Eles continuam sendo componentes independentes da arquitetura distribuída.

## Testes

- unitários: dentro do respectivo serviço;
- integração: `tests/integration/`;
- carga: `tests/performance/`;
- falhas: `tests/failure/`.

## Regra para novos serviços

Criar um novo microsserviço somente quando houver uma responsabilidade independente, necessidade de isolamento de falha ou justificativa técnica clara.
