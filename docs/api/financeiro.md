# Contrato financeiro — `ledger-core`

Responsáveis: Jeferson (implementação) e Joseph (integração no Market). Decisão de arquitetura: [ADR-0002](../adr/0002-transacao-unica-nucleo-negociacao.md).

## Como usar no Market Service

1. Dependência no `backend/market-service/pom.xml`:
   ```xml
   <dependency>
       <groupId>br.ufs.stockmarket</groupId>
       <artifactId>ledger-core</artifactId>
       <version>${project.version}</version>
   </dependency>
   ```
2. Numa classe `@Configuration`: `@Import(br.ufs.stockmarket.ledger.LedgerConfiguration.class)`.
3. Injetar `LedgerService` e chamar **somente dentro de um método `@Transactional`**. Sem transação ativa, o ledger lança `IllegalStateException`.
4. Dockerfile do Market: instalar o pai e o `ledger-core` antes de empacotar, como em `backend/portfolio-service/Dockerfile`.
5. Variável `INITIAL_BALANCE` (ou `ledger.initial-balance`): é o saldo de contas criadas pelo ledger. Padrão: 100000.00.

## Operações

| Método | Quando o Market chama | Efeito | Erros |
|---|---|---|---|
| `ensureAccount(userId)` | Opcional; as reservas já chamam | Cria a conta com o saldo inicial, sem corrida | — |
| `reserveBuy(orderId, userId, assetId, limitPrice, qty)` | Ao aceitar uma ordem BUY, **antes** do matching | `reserved_balance += limitPrice × qty` | `InsufficientFundsException` → 422 |
| `reserveSell(orderId, userId, assetId, limitPrice, qty)` | Ao aceitar uma ordem SELL, **antes** do matching | `reserved_quantity += qty` | `InsufficientPositionException` → 422 (sem venda a descoberto) |
| `lockParticipants(assetId, userIds)` | Depois de calcular os matches e antes da primeira liquidação | Bloqueia contas e posições em ordem determinística | — |
| `settleTrade(TradeSettlement)` | Para cada trade, na mesma transação | Ver "Liquidação" | `InvalidSettlementException` → 500 (indica bug) |
| `releaseRemaining(orderId)` | No cancelamento; ou quando a ordem é encerrada com sobra | Devolve o que ainda estava reservado | — (ordem legada sem reserva devolve `found=false`) |

Todas as operações são **idempotentes**: reserva por `orderId`, liquidação por `tradeId` (a segunda chamada devolve `duplicate=true`) e liberação por estado da reserva.

## Liquidação

Para um trade de `q` ações a `p` (preço da ordem **passiva**), em que a compra foi reservada com limite `L`:

| | Efeito |
|---|---|
| Comprador, conta | `balance −= p·q`; `reserved_balance −= L·q`. O excedente `(L−p)·q` volta ao disponível. |
| Comprador, posição | `quantity += q`; `average_price = (qtd·médio + p·q) / (qtd + q)`, com 4 casas e HALF_EVEN |
| Vendedor, conta | `balance += p·q` |
| Vendedor, posição | `quantity −= q`; `reserved_quantity −= q`; o preço médio não muda (vai a 0 se a posição zerar) |
| Reservas | `remaining −= q`; vira `CONSUMED` ao chegar em 0 |

Validações feitas pelo ledger: `p ≤ L` da compra, `p ≥` limite da venda, `q ≤` restante das duas ordens, mesmo ativo e mesmos usuários das reservas, e **autonegociação rejeitada**. A política recomendada para o Market é rejeitar a ordem nova que cruzaria com uma ordem do próprio usuário.

## Sequência recomendada no Market (por ordem recebida)

```text
@Transactional
1. SELECT ... FROM market.assets WHERE id = ? FOR UPDATE        -- serializa o ativo
2. grava a ordem (sequence_number atribuído aqui)
3. ledger.reserveBuy / reserveSell                              -- falha → 422, rollback
4. calcula os matches contra o livro (somente remaining_quantity)
5. ledger.lockParticipants(assetId, {taker} ∪ makers)
6. para cada match: grava o trade, atualiza as ordens, ledger.settleTrade(...)
7. ordens encerradas com sobra: ledger.releaseRemaining(orderId)
8. grava os eventos na outbox
commit
```

Cancelamento: bloquear o ativo (passo 1), mudar o estado da ordem, chamar `ledger.releaseRemaining(orderId)` e gravar o evento na outbox.

Para deadlock ou falha de serialização (`CannotAcquireLockException`, `PessimisticLockingFailureException`): repetir a transação inteira até 3 vezes, com backoff curto. É seguro porque tudo é idempotente.

## Tabelas (schema `portfolio`)

- `accounts`: `balance`, `reserved_balance`. Disponível = balance − reserved.
- `portfolio_positions`: `quantity`, `reserved_quantity`, `average_price`. Disponível = quantity − reserved.
- `order_reservations`: uma linha por ordem, com `side`, `limit_price`, `original_quantity`, `remaining_quantity`, `reserved_amount` e `status` (ACTIVE, CONSUMED ou RELEASED).
- `trade_settlements`: uma linha por trade; a PK `trade_id` garante a idempotência.

## API de consulta (Portfolio Service)

`GET /api/v1/portfolio/me` (JWT). Campos novos, que só se somam aos anteriores:

```json
{
  "userId": "…",
  "balance": 97500.0000,
  "reservedBalance": 1000.0000,
  "availableBalance": 96500.0000,
  "positionsValue": 2600.0000,
  "equity": 100100.0000,
  "positions": [{
    "assetId": "…", "ticker": "TECH3",
    "quantity": 100, "reservedQuantity": 0, "availableQuantity": 100,
    "averagePrice": 25.0000, "currentPrice": 26.0000,
    "marketValue": 2600.0000, "unrealizedPnl": 100.0000
  }]
}
```

`equity = balance + positionsValue`. A reserva não é somada de novo, porque já faz parte de `balance`. Os campos de mercado ficam `null` se o preço do ativo não estiver disponível.
