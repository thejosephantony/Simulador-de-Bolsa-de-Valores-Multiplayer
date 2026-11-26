package br.ufs.stockmarket.market.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "trades", schema = "market")
public class TradeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "asset_id", nullable = false, updatable = false)
    private UUID assetId;

    @Column(name = "buy_order_id", nullable = false, updatable = false)
    private UUID buyOrderId;

    @Column(name = "sell_order_id", nullable = false, updatable = false)
    private UUID sellOrderId;

    @Column(name = "buyer_id", nullable = false, updatable = false)
    private UUID buyerId;

    @Column(name = "seller_id", nullable = false, updatable = false)
    private UUID sellerId;

    @Column(nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal price;

    @Column(nullable = false, updatable = false)
    private long quantity;

    @Column(name = "executed_at", nullable = false, updatable = false)
    private OffsetDateTime executedAt;

    protected TradeEntity() {
    }

    public TradeEntity(UUID assetId, UUID buyOrderId, UUID sellOrderId,
                       UUID buyerId, UUID sellerId, BigDecimal price, long quantity) {
        this.assetId = Objects.requireNonNull(assetId);
        this.buyOrderId = Objects.requireNonNull(buyOrderId);
        this.sellOrderId = Objects.requireNonNull(sellOrderId);
        this.buyerId = Objects.requireNonNull(buyerId);
        this.sellerId = Objects.requireNonNull(sellerId);
        if (buyerId.equals(sellerId) || buyOrderId.equals(sellOrderId)) {
            throw new IllegalArgumentException("Autonegociação não é permitida.");
        }
        if (price == null || price.signum() <= 0 || quantity <= 0) {
            throw new IllegalArgumentException("Preço e quantidade devem ser positivos.");
        }
        this.price = price;
        this.quantity = quantity;
        this.executedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getAssetId() { return assetId; }
    public UUID getBuyOrderId() { return buyOrderId; }
    public UUID getSellOrderId() { return sellOrderId; }
    public UUID getBuyerId() { return buyerId; }
    public UUID getSellerId() { return sellerId; }
    public BigDecimal getPrice() { return price; }
    public long getQuantity() { return quantity; }
    public OffsetDateTime getExecutedAt() { return executedAt; }
    public BigDecimal getGrossAmount() { return price.multiply(BigDecimal.valueOf(quantity)); }
}
