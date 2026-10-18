package br.ufs.stockmarket.market.api;

import br.ufs.stockmarket.market.domain.OrderEntity;
import br.ufs.stockmarket.market.domain.OrderSide;
import br.ufs.stockmarket.market.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        UUID assetId,
        String ticker,
        OrderSide side,
        BigDecimal price,
        long quantity,
        OrderStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime cancelledAt
) {
    public static OrderResponse from(OrderEntity order, String ticker) {
        return new OrderResponse(
                order.getId(),
                order.getAssetId(),
                ticker,
                order.getSide(),
                order.getPrice(),
                order.getQuantity(),
                order.getStatus(),
                order.getCreatedAt(),
                order.getCancelledAt()
        );
    }
}
