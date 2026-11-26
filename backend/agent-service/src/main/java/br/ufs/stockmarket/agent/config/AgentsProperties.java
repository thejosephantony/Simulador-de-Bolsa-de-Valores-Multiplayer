package br.ufs.stockmarket.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.List;

@ConfigurationProperties(prefix = "agents")
public record AgentsProperties(
        String authUrl,
        String marketUrl,
        String portfolioUrl,
        String adminToken,
        boolean startPaused,
        List<AgentConfig> list
) {
    public enum StrategyType { MARKET_MAKER, MOMENTUM }

    /**
     * Limites de um agente.
     *
     * @param maxOrderQuantity quantidade máxima por ordem.
     * @param maxOpenOrders    ordens abertas simultâneas; acima disso o agente só cancela.
     * @param orderTtlSeconds  ordens abertas há mais tempo que isso são canceladas.
     * @param spreadPercent    distância total entre compra e venda do market maker (% do preço).
     */
    public record AgentConfig(
            String name,
            String username,
            String password,
            StrategyType strategy,
            long intervalMs,
            long maxOrderQuantity,
            int maxOpenOrders,
            long orderTtlSeconds,
            BigDecimal spreadPercent
    ) {
    }
}
