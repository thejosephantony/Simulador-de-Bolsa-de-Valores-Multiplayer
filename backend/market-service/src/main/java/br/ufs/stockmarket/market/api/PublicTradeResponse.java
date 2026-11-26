package br.ufs.stockmarket.market.api;

import br.ufs.stockmarket.market.domain.TradeEntity;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PublicTradeResponse(UUID id, UUID assetId, BigDecimal price, long quantity, OffsetDateTime executedAt) {
    public static PublicTradeResponse from(TradeEntity t) {
        return new PublicTradeResponse(t.getId(), t.getAssetId(), t.getPrice(), t.getQuantity(), t.getExecutedAt());
    }
}
