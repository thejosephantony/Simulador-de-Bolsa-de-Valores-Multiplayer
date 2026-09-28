# Services

Serviços backend do sistema.

Estrutura inicial:

- `gateway/` — entrada das APIs e roteamento;
- `auth-service/` — usuários, autenticação e JWT;
- `market-service/` — ativos, ordens, Order Book, Matching Engine e trades;
- `portfolio-service/` — saldo, reservas, carteira e patrimônio;
- `realtime-service/` — WebSocket/STOMP e notificações;
- `agent-service/` — investidores automatizados.

## Regra importante

A separação em serviços deve representar responsabilidades reais. Evite criar novos microsserviços sem uma necessidade técnica clara.
