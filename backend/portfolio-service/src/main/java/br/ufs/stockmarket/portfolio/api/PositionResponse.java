package br.ufs.stockmarket.portfolio.api;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Posição do usuário em um ativo.
 * currentPrice, marketValue e unrealizedPnl ficam nulos se o preço do ativo não estiver disponível.
 */
public record PositionResponse(
        UUID assetId,
        String ticker,
        long quantity,
        long reservedQuantity,
        long availableQuantity,
        BigDecimal averagePrice,
        BigDecimal currentPrice,
        BigDecimal marketValue,
        BigDecimal unrealizedPnl
) {
}
