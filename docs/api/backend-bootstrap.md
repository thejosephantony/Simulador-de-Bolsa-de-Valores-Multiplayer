# Backend bootstrap

## Core services

| Service | Port | Responsibility |
|---|---:|---|
| auth-service | 8081 | identity, authentication and authorization |
| market-service | 8082 | assets, orders, Order Book, Matching Engine and trades |
| portfolio-service | 8083 | balance, reservations, positions and portfolio |

All three services use Java 21, Spring Boot and PostgreSQL.

For the first development stage, the services share one PostgreSQL instance while using separate schemas:

- `auth`
- `market`
- `portfolio`

This reduces local infrastructure complexity while preserving data ownership boundaries between services.

## First implemented API

### List active assets

```http
GET http://localhost:8082/api/v1/assets
```

Expected initial catalog:

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

## Local infrastructure

Copy the environment example:

```bash
cp .env.example .env
```

Then start PostgreSQL, Redis and RabbitMQ:

```bash
docker compose up -d postgres redis rabbitmq
```

Run a service from the repository root:

```bash
mvn -f services/market-service/pom.xml spring-boot:run
```

## Next implementation order

1. registration and login in auth-service;
2. JWT validation;
3. creation and validation of orders;
4. Order Book with price-time priority;
5. Matching Engine;
6. balance and asset reservations;
7. WebSocket and RabbitMQ integration.
