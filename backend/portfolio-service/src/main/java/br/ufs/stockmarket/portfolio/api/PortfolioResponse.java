package br.ufs.stockmarket.portfolio.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record PortfolioResponse(
        UUID userId,
        BigDecimal balance,
        BigDecimal reservedBalance,
        BigDecimal availableBalance,
        List<PositionResponse> positions
) {
}
