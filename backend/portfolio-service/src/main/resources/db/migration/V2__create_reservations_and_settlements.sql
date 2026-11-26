-- Parte 2: reservas por ordem e registros de liquidação.
-- Escritas exclusivamente pelo módulo ledger-core, dentro da transação do Market Service
-- (ver docs/adr/0002-transacao-unica-nucleo-negociacao.md).

-- Uma linha por ordem aceita. Guarda o que foi bloqueado para que liberação e
-- liquidação saibam exatamente quanto devolver, mesmo após execuções parciais.
CREATE TABLE order_reservations (
    order_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    asset_id UUID NOT NULL,
    side VARCHAR(4) NOT NULL CHECK (side IN ('BUY', 'SELL')),
    limit_price NUMERIC(19,4) NOT NULL CHECK (limit_price > 0),
    original_quantity BIGINT NOT NULL CHECK (original_quantity > 0),
    remaining_quantity BIGINT NOT NULL CHECK (remaining_quantity >= 0),
    -- BUY: saldo ainda bloqueado = limit_price * remaining_quantity enquanto ACTIVE.
    -- SELL: sempre 0 (o bloqueio é de quantidade, em portfolio_positions.reserved_quantity).
    reserved_amount NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (reserved_amount >= 0),
    status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE', 'CONSUMED', 'RELEASED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (remaining_quantity <= original_quantity),
    CHECK (side = 'BUY' OR reserved_amount = 0),
    CHECK (status <> 'ACTIVE' OR remaining_quantity > 0),
    CHECK (status = 'ACTIVE' OR reserved_amount = 0)
);

CREATE INDEX idx_order_reservations_user_status
    ON order_reservations(user_id, status);

-- Uma linha por trade liquidado. A PK em trade_id torna a liquidação idempotente.
CREATE TABLE trade_settlements (
    trade_id UUID PRIMARY KEY,
    asset_id UUID NOT NULL,
    buy_order_id UUID NOT NULL REFERENCES order_reservations(order_id),
    sell_order_id UUID NOT NULL REFERENCES order_reservations(order_id),
    buyer_id UUID NOT NULL,
    seller_id UUID NOT NULL,
    price NUMERIC(19,4) NOT NULL CHECK (price > 0),
    quantity BIGINT NOT NULL CHECK (quantity > 0),
    gross_amount NUMERIC(19,4) NOT NULL CHECK (gross_amount > 0),
    -- Excedente devolvido ao comprador quando executa abaixo do preço limite.
    buyer_released_excess NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (buyer_released_excess >= 0),
    settled_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (buyer_id <> seller_id),
    CHECK (buy_order_id <> sell_order_id)
);

CREATE INDEX idx_trade_settlements_buyer ON trade_settlements(buyer_id, settled_at DESC);
CREATE INDEX idx_trade_settlements_seller ON trade_settlements(seller_id, settled_at DESC);
