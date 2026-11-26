# Contrato da Outbox do Market — versão 1

A tabela market.outbox_events é escrita dentro da transação de negociação; o rollback remove os eventos junto com as demais alterações. A publicação é feita pelo OutboxPublisher depois do commit. Não consumir diretamente o banco pelo Realtime Service.

Envelope: eventId (UUID), type, version, assetId, sequence (BIGINT), timestamp (UTC ISO 8601), correlationId (UUID da ordem que disparou a operação, somente em eventos privados), userId (somente privados), data (objeto JSON). Eventos públicos não devem expor IDs de ordens, correlationId interno nem investidores. A sequência é global no banco, pode ter lacunas e não representa ordem global de commit entre ativos diferentes; não esperar números consecutivos. Por ativo, o Market serializa as mutações. Consumidores deduplicam por eventId e consultam REST após reconexão ou evento de invalidação. Não aplicar snapshots antigos sobre estados novos.

| type | escopo | data |
|---|---|---|
| order.changed | privado do usuário | orderId, side, status, price, quantity, remainingQuantity, filledQuantity |
| portfolio.changed | privado do usuário | {} (reconsultar carteira) |
| book.changed | público | {} (reconsultar livro e indicadores) |
| trade.executed | público | tradeId, price, quantity, executedAt |

Routing keys (alinhar com a equipe): `market.public.book.changed`, `market.public.trade.executed`, `market.private.order.changed`, `market.private.portfolio.changed`. Mensageria interna apenas; Realtime autoriza cada entrega privada pelo principal JWT e não disponibiliza inscrição em identificadores arbitrários de usuários.

## Publicação e recuperação

Exchange topic durável `market.events`; fila durável `market.realtime.events`, com bindings `market.public.#` e `market.private.#`. O Realtime deve consumir essa fila, não criar uma fila com configuração incompatível. O broker é interno; os eventos privados nunca são publicados diretamente para navegadores. Se houver múltiplas instâncias Realtime, uma fila compartilhada distribui mensagens entre elas, não faz broadcast; para o cenário atual usar uma instância ou projetar filas por instância.

O publisher busca um evento elegível por transação (`FOR UPDATE SKIP LOCKED`). Envia JSON UTF-8 persistente, messageId = eventId; aguarda publisher confirm por até 5 segundos e verifica retorno por falta de rota. Só então preenche `published_at`. O broker pode receber um evento antes de uma falha no commit do publisher: entrega pelo menos uma vez, consumidores deduplicam por eventId. Não é garantia de processamento pelo consumidor.

Em falha, incrementa `attempts`, registra `last_error` e agenda uma nova tentativa com backoff de 1 a 60 segundos; retentativas continuam automaticamente, sem descartar o evento. Não há ordem estrita de entrega sob retries ou múltiplos publishers. Consumidores tratam mensagens como invalidações e reconsultam o snapshot atual. O polling processa no máximo 20 eventos por execução, intervalo padrão 500 ms. A transação de publicação pode ocupar uma conexão enquanto espera o ACK; não bloqueia a transação financeira do mesmo evento já confirmado.

`MARKET_OUTBOX_ENABLED=false` desativa o polling sem afetar o registro transacional. `MARKET_OUTBOX_DELAY_MS` ajusta o intervalo. A saúde geral do Market não depende do broker (a negociação continua em indisponibilidade); monitorar pendências, tentativas e logs `outbox_retry` separadamente. A fila e exchange são declaradas pelo Market, com volume persistente do RabbitMQ. DLQ e tratamento de falhas de consumo são responsabilidade do consumidor e devem ser acordados com Jeferson/Thayla.

A CI inclui `scripts/outbox-smoke-test.sh`, que verifica eventos publicados, interrompe o broker, cria uma ordem, confirma persistência e retry, restaura o broker e verifica drenagem. Executar somente no ambiente de teste isolado.
