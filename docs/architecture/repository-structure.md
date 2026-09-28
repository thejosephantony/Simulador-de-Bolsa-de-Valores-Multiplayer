# Estrutura do repositório

O projeto utiliza um **monorepo**: frontend, serviços, infraestrutura, testes e documentação ficam no mesmo repositório.

## Estrutura

```text
.
├── frontend/
├── services/
│   ├── gateway/
│   ├── auth-service/
│   ├── market-service/
│   ├── portfolio-service/
│   ├── realtime-service/
│   └── agent-service/
├── ai-service/
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

## Ordem recomendada para começar

Não é necessário implementar todos os serviços imediatamente.

1. **Infraestrutura local**
   - PostgreSQL;
   - Redis;
   - RabbitMQ.

2. **Auth Service**
   - cadastro;
   - login;
   - JWT;
   - identificação do usuário.

3. **Market Service**
   - ativos;
   - ordens;
   - Order Book;
   - Matching Engine.

4. **Portfolio Service**
   - saldo;
   - reservas;
   - carteira;
   - patrimônio.

5. **Frontend**
   - login;
   - lista de ativos;
   - ticket de ordem;
   - ordens;
   - carteira.

6. **Realtime Service**
   - WebSocket;
   - atualizações do livro, ordens e carteira.

7. **Agent Service**
   - investidores automatizados baseados em regras.

8. **Gateway, observabilidade e AI Service**
   - introduzir conforme a integração justificar.

## Regra para novos serviços

Um novo microsserviço só deve ser criado quando houver uma responsabilidade independente clara, necessidade de isolamento de falha ou razão técnica documentável. Evite criar serviços apenas para aumentar a quantidade de componentes distribuídos.

## Testes

- testes unitários: dentro do respectivo serviço/aplicação;
- testes de integração: `tests/integration/`;
- testes de carga: `tests/performance/`;
- cenários de falha: `tests/failure/`.
