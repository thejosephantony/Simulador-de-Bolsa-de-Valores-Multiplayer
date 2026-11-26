# Consumo da fila `market.realtime.events` — retry e DLQ

Responsáveis: Jeferson (infraestrutura do broker) e Thayla (consumidor no Realtime Service). Complementa o contrato de publicação em [market-outbox.md](market-outbox.md).

## Topologia

```text
Market (outbox) ──► market.events (topic) ──► market.realtime.events ──► Realtime Service
                                                     │ rejeitada sem requeue
                                                     ▼
                                    market.events.dlx (fanout) ──► market.realtime.events.dlq
```

O DLX é aplicado à fila por policy (`infra/rabbitmq/setup.sh`). Por isso, o consumidor **não deve redeclarar** `market.realtime.events` com argumentos próprios; só consumir.

## O que o consumidor deve fazer

1. **ACK manual ou automático do Spring, depois de processar.** Mensagem processada recebe ACK.
2. **Retry local, limitado.** Até 3 tentativas, com backoff de 1 s, multiplicado por 2. Depois disso, rejeitar **sem requeue**; o broker move a mensagem para a DLQ. Configuração sugerida em Spring Boot:
   ```yaml
   spring:
     rabbitmq:
       listener:
         simple:
           acknowledge-mode: auto
           default-requeue-rejected: false
           retry:
             enabled: true
             max-attempts: 3
             initial-interval: 1s
             multiplier: 2
   ```
3. **Mensagem inválida** (JSON malformado ou `type` desconhecido): rejeitar direto, sem retry (`AmqpRejectAndDontRequeueException`).
4. **Deduplicar por `messageId`** (= `eventId`). A entrega é *at least once*.
5. **Tratar eventos como invalidação.** Ao receber um evento, encaminhar a notificação STOMP; o cliente reconsulta o REST. Não montar estado a partir de eventos.

## Operação da DLQ

- Ver as mensagens: painel em http://localhost:15672, fila `market.realtime.events.dlq`, opção "Get messages". O header `x-death` mostra a fila de origem, o motivo e a contagem.
- Perder uma notificação não corrompe estado: o cliente converge pelo REST ao reconectar. Por isso não há reprocessamento automático da DLQ; ela serve para diagnóstico.
