# Frontend — Home Broker

Aplicação **Home Broker** em React + TypeScript (Vite).

## Parte 1 — o que está implementado

- **Autenticação**: login (usuário ou e-mail) e cadastro, com JWT enviado em todas as requisições;
- **Entrada no mercado**: após o login, a carteira é provisionada com o saldo virtual inicial;
- **Visualização dos ativos**: lista com preço atual e variação;
- **Livro de Ofertas** do ativo selecionado (clicar em um nível preenche o preço da ordem);
- **Envio de ordens** de compra e venda;
- **Minhas ordens**, com cancelamento de ordens abertas;
- **Carteira**: saldo disponível, total, reservado e posições;
- **Atualização básica**: polling periódico (padrão: 2 s) de livro, ordens e carteira, com indicador de conexão.

Na próxima etapa, o polling será substituído por WebSocket/STOMP (`backend/realtime-service`).

## Executando

Com o backend rodando (`docker compose up --build` na raiz do repositório):

```bash
cd frontend
cp .env.example .env   # opcional: os valores padrão já apontam para localhost
npm install
npm run dev
```

Acesse `http://localhost:5173`. A porta precisa ser 5173 por causa do CORS configurado no backend.

## Demonstração com múltiplos clientes

A sessão fica no `sessionStorage`, então cada aba tem a própria sessão:

1. abra duas abas (ou uma janela normal e uma anônima);
2. crie um usuário diferente em cada uma;
3. envie uma ordem em uma aba — ela aparece no Livro de Ofertas da outra em até 2 segundos.

## Variáveis de ambiente

| Variável | Padrão |
|---|---|
| `VITE_AUTH_API_URL` | `http://localhost:8081` |
| `VITE_MARKET_API_URL` | `http://localhost:8082` |
| `VITE_PORTFOLIO_API_URL` | `http://localhost:8083` |
| `VITE_POLLING_INTERVAL_MS` | `2000` |

## Estrutura

```text
src/
├── api/          # cliente HTTP, endpoints e tipos dos serviços
├── auth/         # contexto de sessão (login, cadastro, logout, expiração do JWT)
├── hooks/        # usePolling — atualização periódica
├── components/   # painéis do Home Broker
├── pages/        # LoginPage e HomeBrokerPage
└── utils/        # formatação (moeda, quantidade, horário)
```
