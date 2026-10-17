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

### Criar uma ordem

```http
POST http://localhost:8082/api/v1/orders
Content-Type: application/json

{
  "userId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
  "assetId": "11111111-1111-1111-1111-111111111111",
  "side": "BUY",
  "price": 25.1234,
  "quantity": 100
}
```

Retorna `201 Created` com a ordem persistida em `PENDING`. Contrato, regras de
validação, respostas de erro e limites desta etapa: [Ordens](orders.md).

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
3. Order Book com prioridade preço-tempo;
4. Matching Engine;
5. reservas de saldo e ativos;
6. WebSocket e RabbitMQ.
