# Backend

Backend distribuído do Simulador de Bolsa de Valores Multiplayer.

## Estado da Parte 1

O backend da primeira entrega está funcional com:

- **Auth Service** — cadastro, login, BCrypt, JWT e `/users/me`;
- **Market Service** — catálogo de ativos, criação/consulta/cancelamento de ordens e Livro de Ofertas básico;
- **Portfolio Service** — saldo virtual inicial e consulta da carteira;
- PostgreSQL e migrations Flyway;
- autenticação compartilhada entre serviços;
- Docker Compose;
- health checks;
- smoke test de integração no GitHub Actions.

A documentação dos endpoints está em `docs/api/part1-backend.md`.

## Estrutura arquitetural

- `auth-service/` — usuários, autenticação e autorização;
- `market-service/` — ativos, ordens, Order Book, Matching Engine e trades;
- `portfolio-service/` — saldo, reservas, carteira e patrimônio;
- `realtime-service/` — sincronização WebSocket/STOMP;
- `agent-service/` — investidores automatizados;
- `gateway/` — entrada e roteamento das APIs;
- `ai-service/` — Machine Learning opcional e desacoplado do núcleo transacional.

Os serviços Java compartilham o Maven parent em `backend/pom.xml`, mas mantêm responsabilidades e schemas de dados separados.

## Limite atual

Na Parte 1, as ordens são recebidas, validadas estruturalmente, persistidas e disponibilizadas no Livro de Ofertas como `OPEN`. O Matching Engine, a reserva/liquidação financeira e a mensageria de domínio entram na próxima etapa.

## Regra arquitetural

Um novo serviço só deve ser criado quando houver responsabilidade independente, necessidade de isolamento de falha ou justificativa clara de Sistemas Distribuídos. O Matching Engine permanece sob responsabilidade do `market-service`.
