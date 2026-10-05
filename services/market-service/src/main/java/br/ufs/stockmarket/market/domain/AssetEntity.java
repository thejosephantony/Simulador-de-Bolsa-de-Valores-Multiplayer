package br.ufs.stockmarket.market.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "assets", schema = "market")
public class AssetEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 12)
    private String ticker;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "initial_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal initialPrice;

    @Column(name = "current_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal currentPrice;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected AssetEntity() {
    }

    public UUID getId() {
        return id;
    }

    public String getTicker() {
        return ticker;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getInitialPrice() {
        return initialPrice;
    }

    public BigDecimal getCurrentPrice() {
        return currentPrice;
    }

    public boolean isActive() {
        return active;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
