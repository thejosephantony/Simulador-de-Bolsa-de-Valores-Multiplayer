package br.ufs.stockmarket.market.application;

import br.ufs.stockmarket.market.api.AssetResponse;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Service
public class AssetService {
    private final JdbcClient jdbc;
    public AssetService(DataSource dataSource) { this.jdbc = JdbcClient.create(dataSource); }

    @Transactional(readOnly = true)
    public List<AssetResponse> listActiveAssets() {
        // Single statement: one PostgreSQL snapshot for quotes, volume and best offers.
        return jdbc.sql("""
                SELECT a.id, a.ticker, a.name, a.initial_price, a.current_price,
                       COALESCE((SELECT SUM(t.quantity) FROM market.trades t WHERE t.asset_id = a.id), 0) AS traded_volume,
                       (SELECT MAX(o.price) FROM market.orders o WHERE o.asset_id = a.id
                         AND o.side = 'BUY' AND o.status IN ('OPEN', 'PARTIALLY_FILLED')) AS best_bid,
                       (SELECT MIN(o.price) FROM market.orders o WHERE o.asset_id = a.id
                         AND o.side = 'SELL' AND o.status IN ('OPEN', 'PARTIALLY_FILLED')) AS best_ask
                FROM market.assets a WHERE a.active ORDER BY a.ticker
                """).query((rs, n) -> new AssetResponse(
                rs.getObject("id", UUID.class), rs.getString("ticker"), rs.getString("name"),
                rs.getBigDecimal("initial_price"), rs.getBigDecimal("current_price"),
                priceChangePercent(rs.getBigDecimal("initial_price"), rs.getBigDecimal("current_price")),
                rs.getBigDecimal("traded_volume"), rs.getBigDecimal("best_bid"), rs.getBigDecimal("best_ask")))
                .list();
    }

    static BigDecimal priceChangePercent(BigDecimal initial, BigDecimal current) {
        return current.subtract(initial).multiply(BigDecimal.valueOf(100)).divide(initial, 4, RoundingMode.HALF_EVEN);
    }
}
