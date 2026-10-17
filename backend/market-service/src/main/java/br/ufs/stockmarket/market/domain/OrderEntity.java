package br.ufs.stockmarket.market.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "orders", schema = "market")
public class OrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "asset_id", nullable = false, updatable = false)
    private UUID assetId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 4, updatable = false)
    private OrderSide side;

    @Column(nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal price;

    @Column(nullable = false, updatable = false)
    private long quantity;

    @Column(name = "remaining_quantity", nullable = false)
    private long remainingQuantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Generated(event = EventType.INSERT)
    @Column(name = "sequence_number", nullable = false, insertable = false, updatable = false)
    private Long sequenceNumber;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected OrderEntity() {
    }

    public OrderEntity(UUID userId, UUID assetId, OrderSide side, BigDecimal price, long quantity) {
        this.userId = Objects.requireNonNull(userId);
        this.assetId = Objects.requireNonNull(assetId);
        this.side = Objects.requireNonNull(side);
        this.price = Objects.requireNonNull(price);
        if (price.signum() <= 0 || quantity <= 0) {
            throw new IllegalArgumentException("Preço e quantidade devem ser maiores que zero");
        }
        this.quantity = quantity;
        this.remainingQuantity = quantity;
        this.status = OrderStatus.PENDING;
        this.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getAssetId() { return assetId; }
    public OrderSide getSide() { return side; }
    public BigDecimal getPrice() { return price; }
    public long getQuantity() { return quantity; }
    public long getRemainingQuantity() { return remainingQuantity; }
    public OrderStatus getStatus() { return status; }
    public Long getSequenceNumber() { return sequenceNumber; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public long getVersion() { return version; }
}
