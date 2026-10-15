# AI Service

Componente **opcional** para experimentos de Machine Learning.

A primeira versão dos investidores automatizados deve funcionar sem este serviço, usando estratégias baseadas em regras no `agent-service`.

Se implementado, a proposta é usar Python + FastAPI + scikit-learn e expor apenas sinais/inferências. O AI Service não deve alterar saldo, carteira, ordens ou trades diretamente.
