# Backend bootstrap

## Core services

| Service | Port | Responsibility |
|---|---:|---|
| auth-service | 8081 | identity, authentication and authorization |
| market-service | 8082 | assets, orders, Order Book, Matching Engine and trades |
| portfolio-service | 8083 | balance, reservations, positions and portfolio |

Os serviços centrais ficam em `backend/` e usam Java 21, Spring Boot e PostgreSQL.

Na fase inicial, os serviços compartilham uma instância PostgreSQL usando schemas separados:

- `auth`
- `market`
- `portfolio`

Isso reduz a complexidade local sem quebrar as fronteiras de responsabilidade.

## API implementada

### Listar ativos ativos

```http
GET http://localhost:8082/api/v1/assets
```

Catálogo inicial:

- TECH3
- BANK4
- ENER3
- FOOD3
- RETL3

## Health checks

```text
GET http://localhost:8081/actuator/health
GET http://localhost:8082/actuator/health
GET http://localhost:8083/actuator/health
```

## Execução local

```bash
cp .env.example .env
docker compose up -d postgres redis rabbitmq
```

Executar somente o Market Service:

```bash
mvn -f backend/market-service/pom.xml spring-boot:run
```

Compilar todos os serviços Java:

```bash
mvn -f backend/pom.xml clean verify
```

## Próxima ordem de implementação

1. cadastro e login no auth-service;
2. JWT;
3. criação e validação de ordens;
4. Order Book com prioridade preço-tempo;
5. Matching Engine;
6. reservas de saldo e ativos;
7. WebSocket e RabbitMQ.
