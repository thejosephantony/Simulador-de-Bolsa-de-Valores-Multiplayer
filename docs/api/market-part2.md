# Market — contrato parcial da Parte 2

## Idempotência
POST /api/v1/orders aceita o cabeçalho opcional `Idempotency-Key` (1–128 caracteres ASCII: letras, números, ponto, sublinhado, dois-pontos ou hífen). O cliente deve gerar uma chave por intenção de compra/venda e reutilizar a mesma chave e payload em uma nova tentativa. Sem o cabeçalho, cada chamada é uma nova intenção (compatibilidade com a Parte 1). Uma chave é isolada pelo usuário do JWT. Repetir a mesma intenção devolve a ordem existente em seu estado atual; não é uma cópia da resposta original. O mesmo cabeçalho com outros dados retorna 409. Rejeições/rollback não consomem a chave. O registro e a ordem são confirmados na mesma transação; a chave é adquirida antes dos bloqueios do ativo e das contas. Política atual: sem expiração, preservando o histórico.

## Histórico
- GET /api/v1/assets/{assetId}/trades?limit=50 — público, sem IDs de usuários ou ordens. Campos: id, assetId, price, quantity, executedAt.
- GET /api/v1/trades/me?limit=50 — JWT obrigatório. Campos: id, assetId, orderId (somente próprio), side, price, quantity, grossAmount, executedAt.
- Limite ajustado entre 1 e 100. Ordenação por executedAt e id descendentes. Sem paginação por cursor nesta etapa.

Os testes integrados devem verificar também duas requisições simultâneas com a mesma chave, uso da mesma chave por usuários diferentes, retry após rollback e o isolamento do histórico. O smoke cobre repetição sequencial, conflito de payload e privacidade básica. A interface deve incluir o cabeçalho e reutilizá-lo nas tentativas; até lá, a idempotência não protege clientes que não o enviem.


## Indicadores de mercado
GET /api/v1/assets mantém todos os campos anteriores e acrescenta `priceChangePercent` (variação desde o preço inicial do simulador, em %), `tradedVolume` (quantidade acumulada de ações executadas desde a criação do simulador, não volume diário), `bestBid` e `bestAsk` (preço ou null quando não há oferta). Uma única consulta SQL fornece um snapshot consistente dos indicadores.

## Outbox
Eventos persistidos em `market.outbox_events` na mesma transação de ordens, trades e ledger. Uma repetição idempotente não cria novos eventos. O contrato do envelope e a separação público/privado estão em `docs/events/market-outbox.md`. A publicação AMQP é assíncrona, com confirmação do broker e retentativas; a entrega é at-least-once. Consulte o contrato da outbox.

## Conflitos transacionais
Criação e cancelamento passam por OrderCommandService, fora da transação. Apenas SQLSTATE 40P01 (deadlock) e 40001 (serialization_failure) causam nova tentativa da operação completa, com até três tentativas totais e intervalos de 25 e 50 ms. A chave de idempotência permanece a mesma. Se o limite for atingido, a API retorna HTTP 503. Falhas de negócio e falhas de conexão com resultado de commit incerto não são repetidas automaticamente. O cliente deve reutilizar a chave original ao repetir uma intenção. Os testes unitários simulam exceções; a confirmação do rollback e da concorrência no PostgreSQL exige execução integrada.
