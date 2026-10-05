package br.ufs.stockmarket.portfolio.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "portfolio_positions", schema = "portfolio")
public class PortfolioPositionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "asset_id", nullable = false)
    private UUID assetId;

    @Column(nullable = false)
    private long quantity;

    @Column(name = "reserved_quantity", nullable = false)
    private long reservedQuantity;

    @Column(name = "average_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal averagePrice;

    @Version
    @Column(nullable = false)
    private long version;

    protected PortfolioPositionEntity() {
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

    public long getQuantity() {
        return quantity;
    }

    public long getReservedQuantity() {
        return reservedQuantity;
    }

    public BigDecimal getAveragePrice() {
        return averagePrice;
    }

    public long getVersion() {
        return version;
    }
}
