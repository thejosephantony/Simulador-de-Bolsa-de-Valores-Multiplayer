package br.ufs.stockmarket.ledger;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Dados de um trade a liquidar. {@code price} é o preço de execução (preço da ordem passiva).
 */
public record TradeSettlement(
        UUID tradeId,
        UUID assetId,
        UUID buyOrderId,
        UUID sellOrderId,
        UUID buyerId,
        UUID sellerId,
        BigDecimal price,
        long quantity
) {
    public TradeSettlement {
        Objects.requireNonNull(tradeId, "tradeId");
        Objects.requireNonNull(assetId, "assetId");
        Objects.requireNonNull(buyOrderId, "buyOrderId");
        Objects.requireNonNull(sellOrderId, "sellOrderId");
        Objects.requireNonNull(buyerId, "buyerId");
        Objects.requireNonNull(sellerId, "sellerId");
        Objects.requireNonNull(price, "price");
    }
}
