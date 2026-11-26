# Dados de demonstração e emissão inicial

Criados automaticamente pelas migrations, no primeiro `docker compose up`:

- `backend/auth-service/.../V2__seed_demo_users.sql`: usuários com UUID fixo.
- `backend/portfolio-service/.../V3__seed_demo_accounts_and_initial_emission.sql`: saldo e posições.

> Senhas públicas, **somente para o ambiente acadêmico de demonstração**.

## Contas

| Usuário | Senha | UUID | Saldo inicial | Ações por ativo | Uso |
|---|---|---|---|---|---|
| `demo_investidor1` | `Demo@2026!` | `00000000-0000-4000-a000-000000000101` | 100 000,00 | 200 | Demonstração em dois navegadores |
| `demo_investidor2` | `Demo@2026!` | `00000000-0000-4000-a000-000000000102` | 100 000,00 | 200 | Demonstração em dois navegadores |
| `agent_market_maker` | `Agent@2026!` | `00000000-0000-4000-a000-000000000201` | 1 000 000,00 | 5 000 | Agente provedor de liquidez |
| `agent_momentum` | `Agent@2026!` | `00000000-0000-4000-a000-000000000202` | 200 000,00 | 500 | Agente tomador |

Usuários cadastrados pela tela recebem a conta com `INITIAL_BALANCE` (padrão 100 000,00) e **nenhuma ação**: começam comprando.

## Emissão inicial

Sem venda a descoberto, alguém precisa possuir ações para que o mercado tenha oferta. A emissão é fixa e acontece uma única vez, na migration:

| Ativo | Preço inicial (= preço médio inicial) | Ações emitidas |
|---|---|---|
| TECH3 | 25,00 | 5 900 |
| BANK4 | 18,00 | 5 900 |
| ENER3 | 32,00 | 5 900 |
| FOOD3 | 14,50 | 5 900 |
| RETL3 | 21,00 | 5 900 |

Depois disso, a quantidade total de cada ativo **não muda**: trades só transferem ações entre contas. O preço atual passa a ser o do último trade.

## Regras que impedem alterações arbitrárias

- Não existe endpoint que crie saldo ou ações. Saldo e posições só mudam por reserva, liquidação e liberação (`ledger-core`), dentro da transação do Market.
- Venda sem posição disponível é rejeitada (`InsufficientPositionException`, HTTP 422).
- O banco recusa saldo, reserva ou quantidade negativos, e reserva maior que o total (CHECKs).
- Os seeds usam `ON CONFLICT DO NOTHING`: rodar de novo, ou rodar num banco que já tenha esses nomes, não duplica nem sobrescreve nada.

## Recriar do zero

Os seeds só rodam em banco novo. Para voltar ao estado inicial num ambiente de **teste**:
`docker compose down -v && docker compose up -d --build`. Isso apaga os volumes; não use num banco com dados que queira manter.
