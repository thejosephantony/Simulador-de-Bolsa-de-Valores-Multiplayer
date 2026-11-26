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

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "orders", schema = "market")
public class OrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "asset_id", nullable = false)
    private UUID assetId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private OrderSide side;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal price;

    @Column(nullable = false)
    private long quantity;

    @Column(name = "remaining_quantity", nullable = false)
    private long remainingQuantity;

    @Column(
        name = "sequence_number",
        nullable = false,
        insertable = false,
        updatable = false
    )
    private Long sequenceNumber;

    @Version
    @Column(nullable = false)
    private long version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private OrderStatus status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    protected OrderEntity() {
    }

    public OrderEntity(
            UUID userId,
            UUID assetId,
            OrderSide side,
            BigDecimal price,
            long quantity
    ) {
        if (userId == null || assetId == null || side == null) {
            throw new IllegalArgumentException(
                "Dados obrigatorios da ordem ausentes."
            );
        }

        if (price == null || price.signum() <= 0) {
            throw new IllegalArgumentException(
                "O preco deve ser positivo."
            );
        }

        if (quantity <= 0) {
            throw new IllegalArgumentException(
                "A quantidade deve ser positiva."
            );
        }

        this.userId = userId;
        this.assetId = assetId;
        this.side = side;
        this.price = price;
        this.quantity = quantity;
        this.remainingQuantity = quantity;
        this.status = OrderStatus.OPEN;
        this.createdAt = OffsetDateTime.now();
    }

    public boolean isActive() {
        return status == OrderStatus.OPEN
                || status == OrderStatus.PARTIALLY_FILLED;
    }

    public void fill(long executedQuantity) {

        if (!isActive()) {
            throw new IllegalStateException(
                "A ordem nao esta ativa."
            );
        }

        if (executedQuantity <= 0) {
            throw new IllegalArgumentException(
                "A quantidade executada deve ser positiva."
            );
        }

        if (executedQuantity > remainingQuantity) {
            throw new IllegalArgumentException(
                "Execucao superior a quantidade restante."
            );
        }

        this.remainingQuantity -= executedQuantity;

        if (remainingQuantity == 0) {
            this.status = OrderStatus.FILLED;
        } else {
            this.status = OrderStatus.PARTIALLY_FILLED;
        }
    }

    public void cancel() {

        if (!isActive()) {
            throw new IllegalStateException(
                "A ordem nao pode ser cancelada."
            );
        }

        this.status = OrderStatus.CANCELLED;
        this.cancelledAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getAssetId() {
        return assetId;
    }

    public OrderSide getSide() {
        return side;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public long getQuantity() {
        return quantity;
    }

    public long getRemainingQuantity() {
        return remainingQuantity;
    }

    public long getFilledQuantity() {
        return quantity - remainingQuantity;
    }

    public Long getSequenceNumber() {
        return sequenceNumber;
    }

    public long getVersion() {
        return version;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getCancelledAt() {
        return cancelledAt;
    }
}
