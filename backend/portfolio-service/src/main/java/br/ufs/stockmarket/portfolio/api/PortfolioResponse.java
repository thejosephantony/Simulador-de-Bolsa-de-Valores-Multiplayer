package br.ufs.stockmarket.portfolio.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Carteira do usuário autenticado.
 * equity = balance + positionsValue (a reserva não é somada de novo: ela já faz parte de balance).
 */
public record PortfolioResponse(
        UUID userId,
        BigDecimal balance,
        BigDecimal reservedBalance,
        BigDecimal availableBalance,
        BigDecimal positionsValue,
        BigDecimal equity,
        List<PositionResponse> positions
) {
}
