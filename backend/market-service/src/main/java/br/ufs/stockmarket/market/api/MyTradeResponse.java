package br.ufs.stockmarket.market.api;

import br.ufs.stockmarket.market.domain.OrderSide;
import br.ufs.stockmarket.market.domain.TradeEntity;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record MyTradeResponse(UUID id, UUID assetId, UUID orderId, OrderSide side,
        BigDecimal price, long quantity, BigDecimal grossAmount, OffsetDateTime executedAt) {
    public static MyTradeResponse from(TradeEntity t, UUID userId) {
        boolean buy = t.getBuyerId().equals(userId);
        if (!buy && !t.getSellerId().equals(userId)) throw new IllegalArgumentException("Trade de outro usuário.");
        return new MyTradeResponse(t.getId(), t.getAssetId(), buy ? t.getBuyOrderId() : t.getSellOrderId(),
                buy ? OrderSide.BUY : OrderSide.SELL, t.getPrice(), t.getQuantity(), t.getGrossAmount(), t.getExecutedAt());
    }
}
