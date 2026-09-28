# ADR-0001 — Utilizar monorepo

**Status:** Aceito

## Contexto

O projeto será desenvolvido por quatro integrantes e contém frontend, múltiplos serviços backend, infraestrutura, testes e documentação. Separar cada componente em um repositório independente aumentaria o custo de integração e configuração sem benefício proporcional para o escopo acadêmico.

## Decisão

Manter todos os componentes do Simulador de Bolsa de Valores Multiplayer em um único repositório, organizados por diretórios de responsabilidade.

## Consequências

### Positivas

- configuração inicial mais simples;
- visão completa da arquitetura em um único local;
- alterações que atravessam frontend, serviços e infraestrutura podem ser revisadas juntas;
- CI e documentação podem ser centralizados;
- facilita o trabalho de uma equipe pequena.

### Limitações

- o repositório crescerá com diferentes tecnologias;
- pipelines devem evitar recompilar componentes desnecessariamente no futuro;
- cada serviço deve manter fronteiras claras mesmo estando no mesmo repositório.
