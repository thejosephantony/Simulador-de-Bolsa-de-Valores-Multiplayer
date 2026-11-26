CREATE TABLE market.order_requests (
    user_id UUID NOT NULL,
    request_key VARCHAR(128) NOT NULL,
    order_id UUID UNIQUE REFERENCES market.orders(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, request_key)
);
