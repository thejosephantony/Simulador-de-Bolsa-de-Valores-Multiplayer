# Backend — Parte 1

Este documento descreve o backend implementado para a primeira entrega do Simulador de Bolsa de Valores Multiplayer.

## Escopo entregue

- cadastro de investidor;
- login;
- autenticação stateless com JWT;
- identificação do usuário autenticado;
- catálogo de ativos;
- saldo virtual inicial e consulta da carteira;
- envio de ordem de compra ou venda;
- consulta das próprias ordens;
- cancelamento de ordem aberta;
- Livro de Ofertas básico compartilhado entre clientes;
- PostgreSQL + Flyway;
- Docker Compose;
- health checks;
- CORS para o frontend local;
- CI com smoke test completo.

## Serviços

| Serviço | Porta | Responsabilidade na Parte 1 |
|---|---:|---|
| auth-service | 8081 | cadastro, login, JWT e usuário atual |
| market-service | 8082 | ativos, ordens e livro básico |
| portfolio-service | 8083 | saldo e posições do investidor |

## Fluxo de autenticação

### Cadastro

```http
POST /api/v1/auth/register
Content-Type: application/json

{
  "username": "joseph",
  "email": "joseph@example.com",
  "password": "SenhaSegura123!"
}
```

O cadastro já retorna um JWT para permitir entrada imediata no Home Broker.

### Login

```http
POST /api/v1/auth/login
Content-Type: application/json

{
  "identifier": "joseph",
  "password": "SenhaSegura123!"
}
```

`identifier` aceita username ou e-mail.

### Usuário atual

```http
GET /api/v1/users/me
Authorization: Bearer <token>
```

## Mercado

### Ativos

```http
GET /api/v1/assets
```

### Criar ordem

```http
POST /api/v1/orders
Authorization: Bearer <token>
Content-Type: application/json

{
  "assetId": "<uuid-do-ativo>",
  "side": "BUY",
  "price": 25.00,
  "quantity": 10
}
```

Na Parte 1, ordens válidas são persistidas como `OPEN`. O Matching Engine e a liquidação financeira completa pertencem à etapa posterior do projeto.

### Minhas ordens

```http
GET /api/v1/orders
Authorization: Bearer <token>
```

### Ordem específica

```http
GET /api/v1/orders/{orderId}
Authorization: Bearer <token>
```

A consulta é isolada por usuário.

### Cancelar ordem

```http
DELETE /api/v1/orders/{orderId}
Authorization: Bearer <token>
```

Somente o dono da ordem pode consultá-la ou cancelá-la por estes endpoints.

### Livro de Ofertas

```http
GET /api/v1/assets/{assetId}/order-book
```

O livro agrega as ordens abertas por nível de preço:

- BUY: maior preço primeiro;
- SELL: menor preço primeiro.

Esse endpoint permite que dois clientes enxerguem o mesmo estado básico do mercado.

## Carteira

```http
GET /api/v1/portfolio/me
Authorization: Bearer <token>
```

Na primeira consulta, a conta virtual do usuário é provisionada de forma idempotente com o saldo inicial configurado em `INITIAL_BALANCE` (padrão: R$ 100.000,00).

## Execução

```bash
cp .env.example .env
docker compose up --build
```

Health checks:

```text
http://localhost:8081/actuator/health
http://localhost:8082/actuator/health
http://localhost:8083/actuator/health
```

## Smoke test

Com o backend em execução:

```bash
bash scripts/part1-smoke-test.sh
```

O script executa o fluxo principal da Parte 1 de ponta a ponta.

## Fora do escopo da Parte 1

Estes itens continuam planejados para a evolução do projeto:

- Matching Engine completo;
- execução parcial e múltiplos matches;
- reserva e liquidação real de saldo/ativos;
- RabbitMQ integrado aos eventos de domínio;
- WebSocket/Reatime Service;
- agentes automatizados;
- eventos econômicos;
- tolerância a falhas avançada;
- observabilidade com Prometheus/Grafana;
- testes de carga.
