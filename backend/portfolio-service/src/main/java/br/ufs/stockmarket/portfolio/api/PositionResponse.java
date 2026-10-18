package br.ufs.stockmarket.portfolio.api;

import br.ufs.stockmarket.portfolio.domain.PortfolioPositionEntity;

import java.math.BigDecimal;
import java.util.UUID;

public record PositionResponse(
        UUID assetId,
        long quantity,
        long reservedQuantity,
        BigDecimal averagePrice
) {
    public static PositionResponse from(PortfolioPositionEntity position) {
        return new PositionResponse(
                position.getAssetId(),
                position.getQuantity(),
                position.getReservedQuantity(),
                position.getAveragePrice()
        );
    }
}
