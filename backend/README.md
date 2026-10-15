# Backend

Backend distribuído do Simulador de Bolsa de Valores Multiplayer.

A organização respeita as fronteiras arquiteturais do projeto:

- `auth-service/` — usuários, autenticação e autorização;
- `market-service/` — ativos, ordens, Order Book, Matching Engine e trades;
- `portfolio-service/` — saldo, reservas, carteira e patrimônio;
- `realtime-service/` — sincronização WebSocket/STOMP;
- `agent-service/` — investidores automatizados;
- `gateway/` — entrada e roteamento das APIs;
- `ai-service/` — Machine Learning opcional e desacoplado do núcleo transacional.

Os serviços Java compartilham o Maven parent em `backend/pom.xml`, mas mantêm responsabilidades e schemas de dados separados.

## Regra arquitetural

Um novo serviço só deve ser criado quando houver responsabilidade independente, necessidade de isolamento de falha ou justificativa clara de Sistemas Distribuídos. O Matching Engine permanece sob responsabilidade do `market-service`.
