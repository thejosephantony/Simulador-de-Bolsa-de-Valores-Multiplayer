CREATE TABLE orders (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    asset_id UUID NOT NULL REFERENCES assets(id),
    side VARCHAR(8) NOT NULL CHECK (side IN ('BUY', 'SELL')),
    price NUMERIC(19,4) NOT NULL CHECK (price > 0),
    quantity BIGINT NOT NULL CHECK (quantity > 0),
    status VARCHAR(24) NOT NULL CHECK (status IN ('OPEN', 'CANCELLED')),
    sequence_number BIGSERIAL NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL,
    cancelled_at TIMESTAMPTZ
);

CREATE INDEX idx_orders_user_created
    ON orders(user_id, created_at DESC);

CREATE INDEX idx_orders_asset_status
    ON orders(asset_id, status);

CREATE INDEX idx_orders_book_buy
    ON orders(asset_id, status, price DESC, created_at ASC)
    WHERE side = 'BUY';

CREATE INDEX idx_orders_book_sell
    ON orders(asset_id, status, price ASC, created_at ASC)
    WHERE side = 'SELL';
