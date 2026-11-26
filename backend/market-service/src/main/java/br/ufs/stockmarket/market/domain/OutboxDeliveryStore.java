package br.ufs.stockmarket.market.domain;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.util.Optional;
import java.util.UUID;

@Repository
public class OutboxDeliveryStore {
    public record PendingEvent(UUID eventId, String type, int attempts, String envelope) {
        public String routingKey() {
            return switch (type) {
                case "order.changed", "portfolio.changed" -> "market.private." + type;
                case "book.changed", "trade.executed" -> "market.public." + type;
                default -> throw new IllegalArgumentException("Unknown event type: " + type);
            };
        }
    }

    private final JdbcClient jdbc;

    public OutboxDeliveryStore(DataSource dataSource) {
        this.jdbc = JdbcClient.create(dataSource);
    }

    /** One event per short independent transaction. Multiple publishers skip locked rows. */
    public Optional<PendingEvent> lockNext() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Publisher requires an active transaction");
        }
        return jdbc.sql("""
                SELECT event_id, event_type, attempts,
                    (jsonb_build_object('eventId', event_id, 'type', event_type,
                        'version', event_version, 'assetId', asset_id, 'sequence', sequence_number,
                        'timestamp', to_char(occurred_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),
                        'data', payload)
                     || CASE WHEN user_id IS NOT NULL THEN
                          jsonb_build_object('userId', user_id, 'correlationId', correlation_id)
                         ELSE '{}'::jsonb END)::text AS envelope
                FROM market.outbox_events
                WHERE published_at IS NULL AND next_attempt_at <= clock_timestamp()
                ORDER BY next_attempt_at, sequence_number
                LIMIT 1 FOR UPDATE SKIP LOCKED
                """).query((rs, n) -> new PendingEvent(rs.getObject("event_id", UUID.class),
                rs.getString("event_type"), rs.getInt("attempts"), rs.getString("envelope"))).optional();
    }

    public void markPublished(UUID id) {
        jdbc.sql("""
                UPDATE market.outbox_events SET published_at = clock_timestamp(), last_error = NULL
                WHERE event_id = :id AND published_at IS NULL
                """).param("id", id).update();
    }

    public void markFailed(UUID id, long delaySeconds, String error) {
        jdbc.sql("""
                UPDATE market.outbox_events
                SET attempts = LEAST(attempts::bigint + 1, 2147483647)::integer,
                    next_attempt_at = clock_timestamp() + CAST(:delay AS bigint) * interval '1 second',
                    last_error = :error
                WHERE event_id = :id AND published_at IS NULL
                """).param("id", id).param("delay", delaySeconds).param("error", error).update();
    }
}
