package br.ufs.stockmarket.market.api;

import java.math.BigDecimal;
import java.util.UUID;

public record AssetResponse(
        UUID id,
        String ticker,
        String name,
        BigDecimal initialPrice,
        BigDecimal currentPrice
) {
}
