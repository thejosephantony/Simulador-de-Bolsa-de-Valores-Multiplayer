# Simulador de Bolsa de Valores Multiplayer

Projeto acadêmico desenvolvido para a disciplina de **Sistemas Distribuídos** da **Universidade Federal de Sergipe (UFS)**.

O objetivo é construir um **mercado financeiro virtual distribuído**, no qual múltiplos investidores possam se conectar simultaneamente, consultar ativos, enviar ordens de compra e venda e acompanhar, em tempo real, as alterações provocadas pelas negociações.

Mais do que reproduzir funcionalidades de uma bolsa de valores, o projeto utiliza esse domínio como cenário para explorar conceitos fundamentais de Sistemas Distribuídos, como **concorrência, consistência, ordenação de eventos, comunicação entre serviços, sincronização de estado, tolerância a falhas, escalabilidade e observabilidade**.

---

## 1. Objetivos

O sistema deverá permitir que vários investidores operem simultaneamente sobre um mesmo mercado virtual.

Entre os principais objetivos estão:

- permitir múltiplos clientes conectados simultaneamente;
- autenticar e identificar cada investidor;
- disponibilizar um Home Broker simplificado;
- manter ativos virtuais negociáveis;
- processar ordens de compra e venda;
- manter um Livro de Ofertas;
- executar negociações por meio de um Matching Engine;
- sincronizar o estado do mercado entre os clientes;
- persistir dados relevantes;
- disponibilizar investidores automatizados;
- tratar desconexões e falhas controladas;
- executar testes de concorrência e carga;
- coletar logs e métricas do sistema.

---

## 2. Conceitos de Sistemas Distribuídos explorados

O projeto foi planejado para trabalhar, de forma prática, os seguintes tópicos:

### Concorrência

Vários investidores podem enviar ordens simultaneamente. O sistema deve impedir condições de corrida e operações inconsistentes.

Exemplo:

```text
Saldo disponível: R$ 1.000

Cliente A → ordem de compra de R$ 800
Cliente B → ordem de compra de R$ 700
```

As duas operações não podem consumir o mesmo saldo simultaneamente.

### Consistência

As partes críticas do sistema devem utilizar consistência forte, especialmente:

- saldo;
- carteira;
- ordens;
- negociações;
- estado interno do Matching Engine.

Informações secundárias poderão utilizar consistência eventual, como:

- ranking;
- dashboards;
- métricas;
- notificações;
- dados analíticos.

### Ordenação de eventos

As ordens deverão seguir a política:

```text
Prioridade de preço + prioridade temporal
```

Para ordens de compra:

1. maior preço;
2. em caso de empate, ordem mais antiga.

Para ordens de venda:

1. menor preço;
2. em caso de empate, ordem mais antiga.

### Comunicação distribuída

O sistema poderá combinar:

- HTTP/REST;
- WebSocket;
- mensageria assíncrona.

### Tolerância a falhas

O sistema deverá considerar situações como:

- desconexão de clientes;
- reconexão;
- indisponibilidade temporária de serviços;
- mensagens duplicadas;
- timeouts;
- falhas de comunicação;
- recuperação de estado.

### Escalabilidade

A aplicação será submetida a diferentes níveis de carga para análise de:

- latência;
- throughput;
- ordens processadas por segundo;
- negociações executadas por segundo;
- consumo de CPU;
- uso de memória;
- comportamento sob sobrecarga.

---

## 3. Arquitetura proposta

A arquitetura inicial será baseada em serviços independentes executados em contêineres.

```text
                         ┌─────────────────────────┐
                         │      Home Broker        │
                         │ React + TypeScript      │
                         └────────────┬────────────┘
                                      │
                               REST / WebSocket
                                      │
                         ┌────────────▼────────────┐
                         │       API Gateway       │
                         │       Spring Boot       │
                         └────────────┬────────────┘
                                      │
              ┌───────────────────────┼───────────────────────┐
              │                       │                       │
              ▼                       ▼                       ▼
     ┌────────────────┐     ┌─────────────────┐     ┌────────────────┐
     │  Auth Service  │     │ Market Service  │     │Portfolio Svc   │
     │                │     │                 │     │                │
     │ Users / JWT    │     │ Matching Engine │     │ Saldo          │
     │ Sessions       │     │ Order Book      │     │ Carteira       │
     └────────────────┘     │ Trades          │     │ Patrimônio     │
                            └────────┬────────┘     └────────┬───────┘
                                     │                       │
                                     └───────────┬───────────┘
                                                 │
                                          ┌──────▼───────┐
                                          │   RabbitMQ   │
                                          └──────┬───────┘
                                                 │
                         ┌───────────────────────┼──────────────────────┐
                         │                       │                      │
                         ▼                       ▼                      ▼
                ┌────────────────┐     ┌────────────────┐    ┌────────────────┐
                │ Realtime Svc   │     │ Agent Service  │    │ Analytics Svc  │
                │ WebSocket      │     │ Bots / IA      │    │ Métricas/Rank  │
                └────────────────┘     └───────┬────────┘    └────────────────┘
                                               │
                                         ┌─────▼─────┐
                                         │ AI Service│
                                         │  Python   │
                                         │ FastAPI   │
                                         └───────────┘
```

---

## 4. Stack tecnológica

### Frontend

- React
- TypeScript
- Vite
- Tailwind CSS
- TanStack Query
- Axios
- WebSocket / STOMP
- Recharts

### Backend

- Java 21
- Spring Boot 3
- Spring Web
- Spring Data JPA
- Spring Security
- Spring WebSocket
- Spring AMQP
- Spring Validation
- Spring Actuator
- Micrometer
- Resilience4j

### Dados

- PostgreSQL
- Redis

### Mensageria

- RabbitMQ

### Inteligência Artificial

- agentes baseados em regras em Java;
- Python;
- FastAPI;
- Pandas;
- NumPy;
- scikit-learn.

### Infraestrutura e observabilidade

- Docker
- Docker Compose
- Prometheus
- Grafana
- GitHub Actions
- k6

---

## 5. Componentes

### API Gateway

Responsável por centralizar o acesso dos clientes aos serviços internos.

Principais responsabilidades:

- roteamento;
- autenticação;
- controle de acesso;
- tratamento uniforme de requisições.

### Auth Service

Responsável por:

- cadastro;
- login;
- autenticação;
- emissão e validação de JWT;
- gerenciamento de sessão;
- identificação do investidor.

### Market Service

Componente central do mercado.

Responsável por:

- recebimento de ordens;
- validação;
- Livro de Ofertas;
- Matching Engine;
- execução das negociações;
- atualização do preço dos ativos.

### Portfolio Service

Responsável por:

- saldo;
- carteira;
- posições;
- preço médio;
- patrimônio;
- lucro e prejuízo.

### Realtime Service

Responsável por distribuir atualizações aos clientes por WebSocket.

Exemplos:

- alteração do Livro de Ofertas;
- execução de ordem;
- alteração de preço;
- atualização de carteira;
- rejeição de ordem;
- eventos do mercado.

### Agent Service

Responsável pelos investidores automatizados.

Perfis inicialmente previstos:

- Market Maker;
- Momentum;
- Contrarian;
- Conservador;
- Agressivo.

### AI Service

Serviço opcional para estratégias de Machine Learning.

O serviço poderá receber dados recentes do mercado e produzir sinais como:

```json
{
  "asset": "TECH3",
  "prediction": "UP",
  "confidence": 0.76
}
```

O modelo não executará negociações diretamente. O resultado será utilizado por um agente, que enviará uma ordem normal ao mercado.

---

## 6. Matching Engine

O Matching Engine será o componente responsável por determinar quando ordens de compra e venda podem ser executadas.

### Compra

```text
1. maior preço;
2. menor timestamp/sequence number.
```

### Venda

```text
1. menor preço;
2. menor timestamp/sequence number.
```

Exemplo:

```text
SELL

100 ações @ R$ 100
200 ações @ R$ 101

BUY

250 ações @ R$ 102
```

Resultado esperado:

```text
Trade 1 → 100 ações @ R$ 100
Trade 2 → 150 ações @ R$ 101
```

A ordem de compra é totalmente executada e restam:

```text
50 ações @ R$ 101
```

no lado de venda.

O Matching Engine também deverá tratar:

- execução parcial;
- múltiplos matches;
- ordens concorrentes;
- saldo reservado;
- ativos reservados;
- atomicidade;
- idempotência.

---

## 7. Livro de Ofertas

Cada ativo possuirá um Livro de Ofertas independente.

Exemplo:

```text
TECH3

VENDA
--------------------------------
R$ 105,00 | 100 ações
R$ 106,00 | 200 ações
R$ 108,00 | 500 ações

COMPRA
--------------------------------
R$ 102,00 | 150 ações
R$ 101,00 | 300 ações
R$  99,00 | 200 ações
```

Enquanto:

```text
bestBid < bestAsk
```

não há negociação.

Quando:

```text
bestBid >= bestAsk
```

o Matching Engine poderá executar o casamento das ordens.

---

## 8. Fluxo de uma ordem

```text
Cliente
   │
   ▼
API Gateway
   │
   ▼
Market Service
   │
   ├── valida autenticação
   ├── valida saldo/ativos
   ├── valida parâmetros
   │
   ▼
Matching Engine
   │
   ├── insere no livro
   │
   └── ou encontra match
           │
           ▼
        Trade
           │
           ▼
       RabbitMQ
      ┌────┼─────────────┐
      ▼    ▼             ▼
Portfolio Realtime   Analytics
      │    │
      ▼    ▼
   Banco  WebSocket
```

Fluxo observável esperado:

```text
ORDER_RECEIVED
       ↓
ORDER_VALIDATED
       ↓
ORDER_BOOK_INSERTED
       ↓
MATCH_FOUND
       ↓
TRADE_EXECUTED
       ↓
PORTFOLIO_UPDATED
       ↓
CLIENTS_NOTIFIED
```

---

## 9. Estados de uma ordem

Uma ordem poderá assumir estados como:

```text
PENDING
OPEN
PARTIALLY_FILLED
FILLED
CANCELLED
REJECTED
```

Modelo conceitual:

```text
Order
├── id
├── userId
├── assetId
├── side
├── price
├── quantity
├── remainingQuantity
├── status
├── sequenceNumber
└── createdAt
```

---

## 10. Persistência

O PostgreSQL será utilizado para armazenar o estado persistente do sistema.

Principais entidades:

```text
users
assets
accounts
portfolio_positions
orders
trades
market_events
transactions
```

O Redis será utilizado para informações de acesso rápido ou natureza efêmera, como:

```text
preços atuais
best bid
best ask
sessões
usuários conectados
ranking temporário
cache de mercado
```

---

## 11. Comunicação assíncrona

O RabbitMQ será utilizado para desacoplar componentes.

Eventos previstos:

```text
ORDER_RECEIVED
ORDER_VALIDATED
ORDER_REJECTED

ORDER_BOOK_UPDATED

MATCH_FOUND
TRADE_EXECUTED

BALANCE_UPDATED
PORTFOLIO_UPDATED

MARKET_PRICE_CHANGED

CLIENT_NOTIFICATION
```

Exemplo:

```json
{
  "event": "TRADE_EXECUTED",
  "asset": "TECH3",
  "price": 35.72,
  "quantity": 100
}
```

---

## 12. Atualização em tempo real

O Home Broker deverá receber atualizações sem recarregar a página.

Exemplos de canais:

```text
/topic/market
/topic/orderbook/{asset}
/topic/trades/{asset}

/user/queue/orders
/user/queue/portfolio
/user/queue/notifications
```

As atualizações poderão incluir:

- novo preço;
- negociação executada;
- atualização do livro;
- status da ordem;
- atualização da carteira;
- evento econômico;
- falha e reconexão.

---

## 13. Home Broker

A interface deverá apresentar, no mínimo:

- saldo disponível;
- patrimônio total;
- ativos possuídos;
- preço médio;
- lucro/prejuízo;
- lista de ativos;
- preço atual;
- variação;
- volume;
- melhores ofertas de compra e venda;
- histórico de ordens;
- histórico de negociações.

Operações mínimas:

- enviar ordem de compra;
- enviar ordem de venda;
- consultar ordem;
- consultar carteira;
- consultar histórico.

---

## 14. Agentes automatizados

Os agentes mantêm o mercado ativo mesmo quando poucos usuários humanos estão conectados.

### Market Maker

Mantém ofertas dos dois lados do mercado.

### Momentum

Compra quando identifica tendência de alta e reduz posição quando a tendência enfraquece.

### Contrarian

Busca oportunidades após movimentos fortes na direção oposta.

### Conservador

Opera menores quantidades e adota limites de risco mais restritos.

### Agressivo

Aceita maior volatilidade e exposição.

Os agentes podem inicialmente ser implementados por regras determinísticas.

---

## 15. Machine Learning

A camada de Machine Learning é um diferencial e não é necessária para o funcionamento básico dos agentes.

Features possíveis:

```text
price_change_1m
price_change_5m
volume
spread
buy_pressure
sell_pressure
order_book_imbalance
volatility
event_impact
```

Saída possível:

```text
UP
DOWN
NEUTRAL
```

Modelos iniciais sugeridos:

- Random Forest;
- Gradient Boosting.

---

## 16. Eventos econômicos

O mercado poderá gerar eventos capazes de alterar a dinâmica dos ativos.

Exemplos:

- resultado empresarial;
- anúncio positivo;
- escândalo corporativo;
- recessão;
- crescimento econômico;
- alteração regulatória;
- escassez de insumos;
- aumento inesperado da demanda.

Exemplo:

```json
{
  "type": "COMPANY_NEWS",
  "asset": "TECH3",
  "sentiment": "POSITIVE",
  "impact": 0.8
}
```

Esses eventos poderão ser publicados no RabbitMQ e consumidos pelo mercado e pelos agentes.

---

## 17. Segurança

O sistema deverá garantir:

- autenticação;
- autorização;
- validação de parâmetros;
- isolamento das operações por usuário;
- proteção de saldo e carteira;
- proteção contra alteração de ordens de terceiros.

Casos que deverão ser rejeitados:

```text
preço negativo
quantidade negativa
compra sem saldo
venda sem ativos
cancelamento de ordem de outro usuário
alteração manual de saldo
requisição não autenticada
```

---

## 18. Tolerância a falhas

Cenários previstos:

| Falha | Comportamento esperado |
|---|---|
| Cliente desconecta | permitir reconexão |
| WebSocket cai | restabelecer conexão |
| RabbitMQ indisponível | retry/backoff |
| AI Service cai | mercado permanece funcional |
| Realtime Service cai | Matching Engine continua operando |
| Requisição duplicada | idempotência |
| Serviço demora | timeout/circuit breaker |

Mecanismos que poderão ser utilizados:

- Retry;
- Timeout;
- Circuit Breaker;
- Exponential Backoff;
- Health Check;
- Dead Letter Queue.

---

## 19. Observabilidade

Ferramentas previstas:

```text
Spring Actuator
Micrometer
Prometheus
Grafana
```

Métricas relevantes:

- requisições por segundo;
- ordens por segundo;
- trades por segundo;
- latência média;
- P95;
- P99;
- conexões WebSocket;
- tamanho das filas;
- CPU;
- memória;
- taxa de erro;
- conexões do banco.

Cada operação poderá possuir um `correlationId` para permitir rastreamento entre serviços.

Exemplo:

```text
ORDER_RECEIVED      correlationId=123
ORDER_VALIDATED     correlationId=123
MATCH_FOUND         correlationId=123
TRADE_EXECUTED      correlationId=123
```

---

## 20. Testes

### Testes unitários

- regras do Matching Engine;
- validações;
- cálculo de carteira;
- estratégias dos agentes.

### Testes de integração

- API + PostgreSQL;
- RabbitMQ;
- Redis;
- autenticação;
- fluxo completo da ordem.

### Testes de concorrência

Exemplos:

- ordens simultâneas;
- utilização concorrente do saldo;
- venda concorrente da mesma posição;
- mensagens duplicadas.

### Testes de carga

Utilização do k6 com cenários como:

```text
5 usuários
20 usuários
50 usuários
100 usuários
250 usuários
500 usuários
```

Métricas:

```text
latência
throughput
ordens/s
trades/s
CPU
RAM
taxa de erros
```

---

## 21. Contêineres

Todos os principais componentes deverão ser executáveis em contêineres.

Estrutura prevista:

```text
frontend
gateway
auth-service
market-service
portfolio-service
realtime-service
agent-service
ai-service
postgres
redis
rabbitmq
prometheus
grafana
```

Execução local prevista:

```bash
docker compose up --build
```

Para encerrar:

```bash
docker compose down
```

> Os comandos acima representam a configuração pretendida do projeto e deverão acompanhar a evolução da implementação.

---

## 22. Estrutura do repositório

Estrutura proposta:

```text
stock-market-simulator/
│
├── frontend/
│
├── services/
│   ├── gateway/
│   ├── auth-service/
│   ├── market-service/
│   ├── portfolio-service/
│   ├── realtime-service/
│   └── agent-service/
│
├── ai-service/
│
├── infra/
│   ├── docker/
│   ├── prometheus/
│   ├── grafana/
│   └── rabbitmq/
│
├── tests/
│   ├── performance/
│   └── integration/
│
├── docs/
│   ├── architecture/
│   ├── adr/
│   ├── diagrams/
│   └── api/
│
├── scripts/
├── docker-compose.yml
└── README.md
```

---

## 23. Decisões arquiteturais

As decisões relevantes deverão ser registradas em ADRs (*Architecture Decision Records*).

Exemplos:

```text
ADR-001 — Java e Spring Boot para os serviços principais
ADR-002 — PostgreSQL como banco relacional
ADR-003 — RabbitMQ para comunicação assíncrona
ADR-004 — WebSocket para atualização em tempo real
ADR-005 — Consistência forte no núcleo transacional
ADR-006 — Redis para estado efêmero e cache
ADR-007 — Docker Compose para execução local
```

---

## 24. Cronograma acadêmico

### Entrega parcial — 21/10/2026

Objetivo: demonstrar um protótipo funcional.

Escopo previsto:

- múltiplos clientes;
- autenticação;
- entrada no mercado;
- visualização dos ativos;
- envio de ordens;
- comunicação cliente-servidor;
- atualização básica do Home Broker.

### Entrega final — 30/11/2026

Escopo previsto:

- Matching Engine completo;
- Livro de Ofertas;
- carteira e patrimônio;
- persistência;
- sincronização;
- agentes automatizados;
- tolerância a falhas;
- reconexão;
- testes de concorrência;
- testes de carga;
- observabilidade;
- segurança;
- documentação;
- demonstração prática.

---

## 25. Demonstração de falha

A apresentação final deverá incluir pelo menos uma falha controlada.

Exemplo:

```text
1. mercado funcionando;
2. clientes conectados;
3. ordens sendo enviadas;
4. Realtime Service é interrompido;
5. Matching Engine permanece operacional;
6. monitoramento detecta a falha;
7. serviço é restaurado;
8. cliente reconecta;
9. estado é recuperado.
```

Durante a demonstração deverão ser explicados:

- componente afetado;
- impacto esperado;
- forma de detecção;
- reação do sistema;
- estado preservado;
- processo de recuperação.

---

## 26. Requisitos mínimos

- [ ] múltiplos clientes;
- [ ] autenticação;
- [ ] Home Broker;
- [ ] ativos;
- [ ] compra e venda;
- [ ] Livro de Ofertas;
- [ ] Matching Engine;
- [ ] sincronização;
- [ ] persistência;
- [ ] agentes automatizados;
- [ ] reconexão e tolerância a falhas;
- [ ] testes de carga;
- [ ] logs e métricas;
- [ ] segurança;
- [ ] execução em contêineres.

---

## 27. Possíveis diferenciais

Após o atendimento integral dos requisitos principais, poderão ser explorados:

- replicação de serviços;
- balanceamento de carga;
- filas e streaming;
- Circuit Breaker;
- descoberta de serviços;
- eleição de líder;
- mecanismos de consenso;
- Event Sourcing;
- CQRS;
- múltiplos mercados;
- agentes com estratégias distintas;
- dashboard avançado de observabilidade;
- testes automatizados de caos;
- simulação com grande volume de investidores;
- Kubernetes.

Esses itens não devem substituir a implementação correta dos requisitos essenciais.

---

## 28. Git e colaboração

Padrão sugerido de branches:

```text
main
develop

feature/auth
feature/order-book
feature/matching-engine
feature/websocket
feature/agents
feature/observability

fix/*
refactor/*
```

Padrão de commits:

```text
feat(auth): implement JWT authentication
feat(market): implement price-time priority
fix(portfolio): prevent concurrent balance update
test(market): add concurrent order tests
docs(architecture): add messaging ADR
```

Cada alteração relevante deverá ser integrada por Pull Request e revisada antes do merge.

---

## 29. Integrantes

| Nome | Responsabilidade |
|---|---|
| A definir | Arquitetura / integração |
| A definir | Matching Engine / concorrência |
| A definir | Backend / persistência |
| A definir | Frontend / Home Broker |
| A definir | Infraestrutura / observabilidade |
| A definir | Agentes / IA / testes |

---

## 30. Disciplina

**Universidade Federal de Sergipe**  
**Disciplina:** Sistemas Distribuídos  
**Professor:** Rafael Oliveira Vasconcelos  
**Projeto:** Simulador de Bolsa de Valores Multiplayer  
**Entrega parcial:** 21/10/2026  
**Entrega final prevista:** 30/11/2026

---

## 31. Licença

Projeto desenvolvido para fins acadêmicos.

A definição de uma licença de software poderá ser realizada posteriormente de acordo com as decisões do grupo.
