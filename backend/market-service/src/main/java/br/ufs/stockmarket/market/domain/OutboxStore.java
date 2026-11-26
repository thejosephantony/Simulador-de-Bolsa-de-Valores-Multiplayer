package br.ufs.stockmarket.market.domain;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import javax.sql.DataSource;
import java.util.Map;
import java.util.UUID;

/** Persists notifications in the same transaction as the financial operation. */
@Repository
public class OutboxStore {
    private final JdbcClient jdbc;
    public OutboxStore(DataSource dataSource) { this.jdbc = JdbcClient.create(dataSource); }

    public void orderChanged(OrderEntity order, UUID correlationId) {
        append("order.changed", order.getAssetId(), order.getUserId(), correlationId,
                "jsonb_build_object('orderId', CAST(:orderId AS uuid), 'side', CAST(:side AS text), 'status', CAST(:status AS text), 'price', CAST(:price AS numeric), 'quantity', CAST(:quantity AS bigint), 'remainingQuantity', CAST(:remaining AS bigint), 'filledQuantity', CAST(:filled AS bigint))",
                Map.of("orderId", order.getId(), "side", order.getSide().name(), "status", order.getStatus().name(),
                        "price", order.getPrice(), "quantity", order.getQuantity(), "remaining", order.getRemainingQuantity(),
                        "filled", order.getFilledQuantity()));
    }

    public void portfolioChanged(UUID assetId, UUID userId, UUID correlationId) {
        append("portfolio.changed", assetId, userId, correlationId, "'{}'::jsonb", Map.of());
    }

    public void bookChanged(UUID assetId, UUID correlationId) {
        append("book.changed", assetId, null, correlationId, "'{}'::jsonb", Map.of());
    }

    public void tradeExecuted(TradeEntity trade, UUID correlationId) {
        // Public trade payload does not expose investor identities or private order identifiers.
        append("trade.executed", trade.getAssetId(), null, correlationId,
                "jsonb_build_object('tradeId', CAST(:tradeId AS uuid), 'price', CAST(:price AS numeric), 'quantity', CAST(:quantity AS bigint), 'executedAt', CAST(:executedAt AS timestamptz))",
                Map.of("tradeId", trade.getId(), "price", trade.getPrice(), "quantity", trade.getQuantity(),
                        "executedAt", trade.getExecutedAt()));
    }

    private void append(String type, UUID assetId, UUID userId, UUID correlationId,
                        String payloadExpression, Map<String, ?> payloadParams) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("OutboxStore exige transacao ativa.");
        }
        // Only fixed, internal SQL expressions are used; values are bound parameters.
        jdbc.sql("INSERT INTO market.outbox_events (event_id, event_type, asset_id, user_id, correlation_id, payload) "
                        + "VALUES (:eventId, :eventType, :assetId, CAST(:userId AS uuid), :correlationId, " + payloadExpression + ")")
                .param("eventId", UUID.randomUUID()).param("eventType", type).param("assetId", assetId)
                .param("userId", userId).param("correlationId", correlationId).params(payloadParams).update();
    }
}
