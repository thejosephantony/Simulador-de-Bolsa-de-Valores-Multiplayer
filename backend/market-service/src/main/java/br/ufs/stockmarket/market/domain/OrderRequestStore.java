package br.ufs.stockmarket.market.domain;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import javax.sql.DataSource;
import java.util.UUID;

/** Claims a key before any asset/account lock. All writes join the caller transaction. */
@Repository
public class OrderRequestStore {
    private final JdbcClient jdbc;
    public OrderRequestStore(DataSource dataSource) { this.jdbc = JdbcClient.create(dataSource); }

    public boolean claim(UUID userId, String key) {
        requireTransaction();
        return jdbc.sql("INSERT INTO market.order_requests (user_id, request_key) VALUES (:uid, :key) ON CONFLICT (user_id, request_key) DO NOTHING")
                .param("uid", userId).param("key", key).update() == 1;
    }

    public UUID existingOrder(UUID userId, String key) {
        requireTransaction();
        return jdbc.sql("SELECT order_id FROM market.order_requests WHERE user_id = :uid AND request_key = :key")
                .param("uid", userId).param("key", key)
                .query((rs, n) -> rs.getObject("order_id", UUID.class)).optional()
                .orElseThrow(() -> new IllegalStateException("Chave sem ordem confirmada."));
    }

    public void complete(UUID userId, String key, UUID orderId) {
        requireTransaction();
        int updated = jdbc.sql("UPDATE market.order_requests SET order_id = :oid WHERE user_id = :uid AND request_key = :key AND order_id IS NULL")
                .param("uid", userId).param("key", key).param("oid", orderId).update();
        if (updated != 1) throw new IllegalStateException("Falha ao vincular a chave à ordem.");
    }

    private void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("OrderRequestStore exige transação ativa.");
        }
    }
}
