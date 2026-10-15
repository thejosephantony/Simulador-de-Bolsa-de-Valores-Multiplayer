# Agent Service

Componente responsável pelos investidores automatizados do mercado.

Estratégias previstas:

- Market Maker;
- Momentum;
- Contrarian;
- Conservador;
- Agressivo.

Os agentes devem enviar ordens pelo fluxo normal do sistema e nunca alterar diretamente saldo, carteira ou trades.

A primeira implementação deve usar regras determinísticas. O `ai-service` é opcional e poderá fornecer sinais de Machine Learning posteriormente.
