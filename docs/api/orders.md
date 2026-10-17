# Criação e validação de ordens

O Market Service recebe ordens limitadas de compra e venda. Nesta etapa, valida
os parâmetros e o ativo e persiste a ordem, seguindo as camadas `api`,
`application` e `domain` existentes.

## Criar ordem

```http
POST /api/v1/orders
Content-Type: application/json

{
  "userId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
  "assetId": "11111111-1111-1111-1111-111111111111",
  "side": "BUY",
  "price": 25.1234,
  "quantity": 100
}
```

Use o identificador retornado por `GET /api/v1/assets` no campo `assetId`.

| Campo | Regra |
|---|---|
| userId | UUID obrigatório |
| assetId | UUID obrigatório, referente a um ativo existente e ativo |
| side | Texto obrigatório: `BUY` ou `SELL`, respeitando maiúsculas |
| price | Obrigatório e positivo; no máximo 15 dígitos inteiros e 4 decimais, compatível com `NUMERIC(19,4)` |
| quantity | Inteiro obrigatório entre `1` e `9223372036854775807` |

Preços usam `BigDecimal`; valores fora da precisão permitida são rejeitados,
sem arredondamento. Quantidades fracionárias e lados numéricos são rejeitados
na leitura do JSON. Campos desconhecidos ou gerenciados pelo servidor também
são rejeitados.

### Resposta: 201 Created

```json
{
  "id": "b4074db9-1469-495b-93c4-424ae6ca1c2b",
  "userId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
  "assetId": "11111111-1111-1111-1111-111111111111",
  "side": "BUY",
  "price": 25.1234,
  "quantity": 100,
  "remainingQuantity": 100,
  "status": "PENDING",
  "sequenceNumber": 1,
  "createdAt": "2026-10-17T13:00:00Z"
}
```

O servidor gera `id`, `sequenceNumber` e `createdAt`. Inicializa
`remainingQuantity` com `quantity` e `status` com `PENDING`. O exemplo usa
identificadores, sequência e horário ilustrativos.

A gravação ocorre em uma transação. A migration `V2__create_orders.sql` adiciona
`market.orders`, com chave estrangeira somente para `market.assets`, restrições
de integridade e versão otimista para futuras alterações concorrentes. A
migration inicial e o catálogo de ativos são preservados.

O Market Service utiliza o starter `spring-boot-starter-flyway` e o módulo
`flyway-database-postgresql`, para executar as migrations automaticamente antes
da validação do schema pelo Hibernate no Spring Boot 4.

A sequência é uma coluna identity única, gerada pelo PostgreSQL e recuperada
antes da resposta. Ela evita contadores locais por instância e fornece uma
referência de ordenação para a próxima etapa. Pode ter lacunas após rollback e
não representa ordem de commit nem substitui a serialização do futuro Matching
Engine. Não há garantia de prioridade temporal baseada no relógio do cliente.

### Respostas de erro

Erros usam `Content-Type: application/problem+json`.

| Status | Situação |
|---|---|
| `400 Bad Request` | Campos obrigatórios ausentes/nulos, preço/quantidade inválidos, JSON malformado, UUID/enum inválido ou campos desconhecidos |
| `422 Unprocessable Entity` | Ativo inexistente ou inativo |

Exemplo de erro de validação:

```json
{
  "type": "about:blank",
  "title": "Parâmetros de ordem inválidos",
  "status": 400,
  "detail": "Corrija os campos indicados e envie a ordem novamente",
  "instance": "/api/v1/orders",
  "errors": [
    { "field": "quantity", "message": "quantity deve ser maior que zero" }
  ]
}
```

Erros de campos e de ativo incluem `errors` com `field` e `message`; erros
de leitura do JSON incluem uma descrição geral. Requisições rejeitadas não
persistem ordens, inclusive com status `REJECTED`.

## Limites desta etapa

O bootstrap ainda não implementa cadastro/login nem JWT. Por isso, `userId` é
recebido no corpo para desenvolvimento local; somente seu formato e presença
são validados. Sua existência e a identidade do solicitante ainda não são
verificadas. Quando a autenticação for integrada, o identificador deverá vir
do principal autenticado.

Saldo, posições e reservas pertencem ao Portfolio Service. A criação atual não
consulta nem altera schemas de outros serviços. `PENDING` indica que apenas
os parâmetros e o ativo foram validados: ainda não há autorização financeira,
reserva de recursos, entrada no livro, execução, trade ou atualização de preço.
Essas integrações devem ocorrer antes de admitir uma ordem para negociação.

Os estados previstos no README (`OPEN`, `PARTIALLY_FILLED`, `FILLED`,
`CANCELLED`, `REJECTED`) são mapeados no enum e no banco para as próximas
etapas. Nesta etapa, somente `PENDING` é produzido.

Cada POST válido cria uma nova ordem; ainda não há chave de idempotência.
Consulta/listagem/cancelamento de ordens e publicação de eventos ficam para
as etapas correspondentes.

## Verificação

- Unitários: `mvn -f backend/pom.xml clean verify`.
- HTTP + PostgreSQL: `python3 tests/integration/test_market_orders.py`, com o
  serviço e um banco de desenvolvimento descartável em execução.
- A CI executa ambos, incluindo as migrations reais, o catálogo existente,
  limites numéricos, rejeições sem persistência e requisições concorrentes.

Detalhes de configuração: [Testes](../../tests/README.md).
