# ADR-0002 — Transação única para o núcleo de negociação

**Status:** Proposto (aguardando validação do grupo)

## Contexto

Na Parte 2, uma ordem aceita precisa reservar saldo ou ações, e cada trade precisa debitar, creditar e transferir ações. Ordens e trades ficam no schema `market`; contas, posições e reservas ficam no schema `portfolio`. Os dois schemas estão no mesmo PostgreSQL.

Se Market e Portfolio escrevessem cada um no seu schema e se chamassem por HTTP, uma falha entre as duas escritas deixaria, por exemplo, um trade gravado sem a liquidação correspondente. `HTTP + @Transactional` não forma uma transação distribuída. Resolver isso exigiria uma Saga, com estados intermediários, compensações, idempotência e reconciliação, o que não cabe no prazo (25–30/11).

## Decisão

1. **Uma única transação PostgreSQL** cobre ordem, trade, reservas, saldo, posições e outbox.
2. **O Market Service é o único escritor** do núcleo de negociação, inclusive das tabelas financeiras.
3. A lógica financeira fica no módulo Maven **`backend/ledger-core`** (`LedgerService`). Ele roda dentro da transação do chamador e nunca faz commit sozinho: todo método exige transação ativa.
4. As migrations das tabelas financeiras continuam no **portfolio-service** (schema `portfolio`). Por isso, o Market sobe depois do Portfolio no Compose.
5. O **Portfolio Service** fica com a API de consulta da carteira e a criação segura de conta (`INSERT ... ON CONFLICT DO NOTHING`).
6. **Ordem global de bloqueio**, para evitar deadlock:
   `ativo (market.assets FOR UPDATE) → contas (por user_id) → posições (por user_id) → reservas`.
   - Matching e cancelamento do mesmo ativo ficam serializados pela linha do ativo.
   - Posições de um ativo só são alteradas sob o bloqueio desse ativo.
   - Contas podem ser disputadas entre ativos diferentes. Por isso, numa transação com vários matches, o Market chama `lockParticipants(assetId, usuários)` uma vez, antes da primeira liquidação.
   - Se ainda ocorrer deadlock ou falha de serialização, o PostgreSQL aborta uma das transações. O Market repete a operação um número limitado de vezes (ver contrato).
7. **Invariantes garantidas também no banco** (CHECK): saldo ≥ 0, reservado ≥ 0, reservado ≤ saldo, quantidade ≥ 0, quantidade reservada ≤ quantidade.

## Consequências

**Vantagens**
- Atomicidade real: não existe trade sem liquidação, nem reserva sem ordem.
- Sem compensação nem reconciliação; um rollback desfaz tudo.
- Testável com PostgreSQL real (`LedgerServiceTest`, via Testcontainers).

**Limitações**
- Acoplamento pelo banco físico compartilhado: o Market conhece o schema `portfolio`.
- Os serviços não escalam o banco de forma independente.
- O desenho se afasta da coordenação distribuída da proposta inicial. A parte distribuída do sistema passa a estar em: eventos via outbox e RabbitMQ, Realtime Service, agentes e recuperação de falhas.

**Alternativa rejeitada:** Saga entre Market e Portfolio com escrita independente. Fica registrada como evolução possível.
