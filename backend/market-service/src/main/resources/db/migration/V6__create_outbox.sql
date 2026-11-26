CREATE TABLE market.outbox_events (
    event_id UUID PRIMARY KEY,
    sequence_number BIGSERIAL NOT NULL UNIQUE,
    event_type VARCHAR(64) NOT NULL,
    event_version INTEGER NOT NULL DEFAULT 1 CHECK (event_version > 0),
    asset_id UUID NOT NULL REFERENCES market.assets(id),
    user_id UUID,
    correlation_id UUID NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    payload JSONB NOT NULL CHECK (jsonb_typeof(payload) = 'object'),
    published_at TIMESTAMPTZ,
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_error TEXT,
    CHECK ((event_type IN ('order.changed', 'portfolio.changed') AND user_id IS NOT NULL)
        OR (event_type IN ('book.changed', 'trade.executed') AND user_id IS NULL))
);
CREATE INDEX idx_outbox_pending ON market.outbox_events(next_attempt_at, sequence_number)
    WHERE published_at IS NULL;
