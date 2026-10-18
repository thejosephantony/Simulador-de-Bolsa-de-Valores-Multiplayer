package br.ufs.stockmarket.market.api;

import java.math.BigDecimal;

public record BookLevelResponse(
        BigDecimal price,
        long quantity,
        long orderCount
) {
}
