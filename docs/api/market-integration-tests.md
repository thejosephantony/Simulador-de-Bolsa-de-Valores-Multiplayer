# Validação integrada do Market (Parte 2)

Executar exclusivamente em ambiente Compose descartável, sem outros clientes ou agentes. O workflow Backend CI inicia o PostgreSQL, RabbitMQ e os serviços, executa os testes e remove os volumes de teste no encerramento. Nunca executar os scripts contra o banco de apresentação ou produção.

## Cenários

| Cenário | Critério de sucesso |
| --- | --- |
| Compra concorrente em dois ativos | 1 ordem aceita, 1 rejeitada com 422; reserva correta; cancelamento devolve reserva |
| Venda concorrente | 2 de 8 ordens aceitas, 6 rejeitadas com 422; quantidade reservada não excede posição |
| Idempotência simultânea | 8 requisições, um identificador, uma ordem, uma reserva, um evento inicial |
| Falha após liquidação | Trigger temporário interrompe inserção na outbox; rollback de ordem, trade, carteira, preço, reserva, chave e eventos; nova tentativa aceita |
| Negociação e histórico | Smoke test verifica execução parcial, valores financeiros, isolamento JWT e indicadores |
| Falha do broker | Smoke test interrompe RabbitMQ, verifica persistência e retry, restaura broker e confirma publicação |

## Comandos no ambiente isolado

```bash
bash scripts/part1-smoke-test.sh
MARKET_ISOLATED_TEST_DB=1 python3 scripts/market-concurrency-test.py
bash scripts/outbox-smoke-test.sh
```

## Limitações

Os testes de concorrência são funcionais, não benchmarks de desempenho. Os testes com 5/20/50/100 clientes, métricas, recuperação da interface e agentes pertencem à validação conjunta. A injeção de falha usa um trigger temporário apenas no banco de CI, removido no bloco `finally`. A configuração assume saldo inicial de 100000 e livro sem ordens abertas. Não registrar resultados como aprovados antes da execução real da CI.
