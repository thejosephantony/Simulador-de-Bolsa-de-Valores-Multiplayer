# RabbitMQ

| Recurso | Tipo | Declarado por |
|---|---|---|
| `market.events` | exchange topic, durável | Market Service (`MarketMessagingConfig`) |
| `market.realtime.events` | fila durável, bindings `market.public.#` e `market.private.#` | Market Service |
| `market.events.dlx` | exchange fanout, durável | `setup.sh` (container `rabbitmq-setup`) |
| `market.realtime.events.dlq` | fila durável ligada ao DLX | `setup.sh` |
| policy `market-realtime-dlx` | aplica `dead-letter-exchange` à fila `market.realtime.events` | `setup.sh` |

Mensagens rejeitadas pelo consumidor sem requeue (`basic.reject`/`nack` com `requeue=false`) vão para
`market.realtime.events.dlq`, com o header `x-death` indicando a fila e o motivo de origem.

Persistência: volume `rabbitmq_data` e `hostname` fixo no Compose (o diretório de dados depende do nome do nó).

Painel: http://localhost:15672 (usuário/senha de `RABBITMQ_DEFAULT_USER`/`RABBITMQ_DEFAULT_PASS`).
Contrato de consumo: [docs/events/realtime-consumer.md](../../docs/events/realtime-consumer.md).
