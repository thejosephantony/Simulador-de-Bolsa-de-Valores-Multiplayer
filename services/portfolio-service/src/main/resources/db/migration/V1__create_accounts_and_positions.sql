CREATE TABLE accounts (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE,
    balance NUMERIC(19,4) NOT NULL CHECK (balance >= 0),
    reserved_balance NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (reserved_balance >= 0),
    version BIGINT NOT NULL DEFAULT 0,
    CHECK (reserved_balance <= balance)
);

CREATE TABLE portfolio_positions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    asset_id UUID NOT NULL,
    quantity BIGINT NOT NULL DEFAULT 0 CHECK (quantity >= 0),
    reserved_quantity BIGINT NOT NULL DEFAULT 0 CHECK (reserved_quantity >= 0),
    average_price NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (average_price >= 0),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_portfolio_user_asset UNIQUE (user_id, asset_id),
    CHECK (reserved_quantity <= quantity)
);

CREATE INDEX idx_portfolio_positions_user_id
    ON portfolio_positions(user_id);

CREATE INDEX idx_portfolio_positions_asset_id
    ON portfolio_positions(asset_id);
