ALTER TABLE market.orders
    ADD COLUMN remaining_quantity BIGINT;

UPDATE market.orders
SET remaining_quantity = quantity;

ALTER TABLE market.orders
    ALTER COLUMN remaining_quantity SET NOT NULL;

ALTER TABLE market.orders
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE market.orders
    DROP CONSTRAINT orders_status_check;

ALTER TABLE market.orders
    ADD CONSTRAINT orders_status_check
    CHECK (
        status IN (
            'OPEN',
            'PARTIALLY_FILLED',
            'FILLED',
            'CANCELLED'
        )
    );

ALTER TABLE market.orders
    ADD CONSTRAINT orders_remaining_quantity_check
    CHECK (remaining_quantity BETWEEN 0 AND quantity);

-- Cancelamento das ordens legadas sem reservas financeiras.
UPDATE market.orders
SET status = 'CANCELLED',
    cancelled_at = CURRENT_TIMESTAMP
WHERE status = 'OPEN';

ALTER TABLE market.orders
    ADD CONSTRAINT orders_execution_state_check
    CHECK (
        (status = 'OPEN' AND remaining_quantity = quantity)
        OR
        (status = 'PARTIALLY_FILLED'
            AND remaining_quantity > 0
            AND remaining_quantity < quantity)
        OR
        (status = 'FILLED' AND remaining_quantity = 0)
        OR
        (status = 'CANCELLED' AND remaining_quantity > 0)
    );

DROP INDEX market.idx_orders_book_buy;
DROP INDEX market.idx_orders_book_sell;

CREATE INDEX idx_orders_book_buy
    ON market.orders(asset_id, price DESC, sequence_number ASC)
    WHERE side = 'BUY'
      AND status IN ('OPEN', 'PARTIALLY_FILLED');

CREATE INDEX idx_orders_book_sell
    ON market.orders(asset_id, price ASC, sequence_number ASC)
    WHERE side = 'SELL'
      AND status IN ('OPEN', 'PARTIALLY_FILLED');
