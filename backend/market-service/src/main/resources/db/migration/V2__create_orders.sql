CREATE TABLE orders (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    asset_id UUID NOT NULL REFERENCES assets(id),
    side VARCHAR(4) NOT NULL CHECK (side IN ('BUY', 'SELL')),
    price NUMERIC(19,4) NOT NULL CHECK (price > 0),
    quantity BIGINT NOT NULL CHECK (quantity > 0),
    remaining_quantity BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (
        status IN ('PENDING', 'OPEN', 'PARTIALLY_FILLED', 'FILLED', 'CANCELLED', 'REJECTED')
    ),
    sequence_number BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL UNIQUE CHECK (sequence_number > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CHECK (remaining_quantity >= 0 AND remaining_quantity <= quantity)
);

CREATE INDEX idx_orders_user_created_at ON orders(user_id, created_at DESC);
CREATE INDEX idx_orders_asset_status_sequence ON orders(asset_id, status, sequence_number);
