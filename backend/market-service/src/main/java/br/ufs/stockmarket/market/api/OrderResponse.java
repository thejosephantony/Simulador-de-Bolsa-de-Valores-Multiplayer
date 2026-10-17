package br.ufs.stockmarket.market.api;

import br.ufs.stockmarket.market.domain.OrderSide;
import br.ufs.stockmarket.market.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        UUID userId,
        UUID assetId,
        OrderSide side,
        BigDecimal price,
        long quantity,
        long remainingQuantity,
        OrderStatus status,
        long sequenceNumber,
        OffsetDateTime createdAt
) {
}
