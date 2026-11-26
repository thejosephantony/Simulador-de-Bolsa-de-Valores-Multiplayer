CREATE TABLE market.trades (
    id UUID PRIMARY KEY,
    asset_id UUID NOT NULL REFERENCES market.assets(id),
    buy_order_id UUID NOT NULL REFERENCES market.orders(id),
    sell_order_id UUID NOT NULL REFERENCES market.orders(id),
    buyer_id UUID NOT NULL,
    seller_id UUID NOT NULL,
    price NUMERIC(19,4) NOT NULL CHECK (price > 0),
    quantity BIGINT NOT NULL CHECK (quantity > 0),
    executed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT trades_distinct_users_check CHECK (buyer_id <> seller_id),
    CONSTRAINT trades_distinct_orders_check CHECK (buy_order_id <> sell_order_id)
);

CREATE INDEX idx_trades_asset_executed
    ON market.trades(asset_id, executed_at DESC, id DESC);
CREATE INDEX idx_trades_buyer_executed
    ON market.trades(buyer_id, executed_at DESC, id DESC);
CREATE INDEX idx_trades_seller_executed
    ON market.trades(seller_id, executed_at DESC, id DESC);
CREATE INDEX idx_trades_buy_order ON market.trades(buy_order_id);
CREATE INDEX idx_trades_sell_order ON market.trades(sell_order_id);
