# Architecture Decision Records (ADR)

Registre aqui decisões arquiteturais relevantes.

Formato sugerido:

```text
# ADR-00X — Título

Status: Proposto | Aceito | Substituído

## Contexto
Qual problema precisava ser resolvido?

## Decisão
O que foi decidido?

## Consequências
Vantagens, limitações e impactos.
```

ADRs iniciais sugeridos:

1. Java 21 + Spring Boot para os serviços backend;
2. PostgreSQL como persistência transacional;
3. REST + WebSocket + AMQP como modelo de comunicação;
4. RabbitMQ como broker de eventos;
5. consistência forte no núcleo financeiro;
6. Docker Compose como ambiente inicial.
