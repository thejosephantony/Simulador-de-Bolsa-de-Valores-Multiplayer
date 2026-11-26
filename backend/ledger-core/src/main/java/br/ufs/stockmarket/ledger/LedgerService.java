package br.ufs.stockmarket.ledger;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Operações financeiras do núcleo de negociação: reserva, liberação e liquidação.
 *
 * <h2>Contrato com o chamador (Market Service)</h2>
 * <ul>
 *   <li>Todo método exige uma transação Spring ativa: o ledger nunca faz commit sozinho. Ordem, trade,
 *       reservas, saldo, posições e outbox são confirmados ou revertidos juntos.</li>
 *   <li>Antes de chamar, o chamador já deve ter bloqueado a linha do ativo
 *       ({@code SELECT ... FROM market.assets WHERE id = ? FOR UPDATE}). Isso serializa matching e
 *       cancelamento por ativo.</li>
 *   <li>Ordem global de bloqueio: <b>ativo → contas (por user_id) → posições (por user_id) → reservas</b>.
 *       Para várias execuções na mesma transação, chame {@link #lockParticipants} uma vez, com todos os
 *       usuários envolvidos, antes da primeira liquidação.</li>
 *   <li>Qualquer {@link LedgerException} deve reverter a transação.</li>
 * </ul>
 *
 * <p>Todas as tabelas são referenciadas com o schema {@code portfolio.} explícito, pois o Market usa
 * {@code market} como schema padrão.</p>
 */
public class LedgerService {

    static final int SCALE = 4;
    private static final Comparator<UUID> LOCK_ORDER = Comparator.comparing(UUID::toString);

    private final JdbcClient jdbc;
    private final BigDecimal initialBalance;

    public LedgerService(JdbcClient jdbc, BigDecimal initialBalance) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.initialBalance = money(Objects.requireNonNull(initialBalance));
    }

    // ------------------------------------------------------------------ contas

    /**
     * Cria a conta com o saldo inicial se ainda não existir. Seguro sob concorrência:
     * duas chamadas simultâneas para o mesmo usuário não geram erro.
     */
    public void ensureAccount(UUID userId) {
        requireTransaction();
        jdbc.sql("""
                        INSERT INTO portfolio.accounts (id, user_id, balance, reserved_balance, version)
                        VALUES (gen_random_uuid(), :userId, :balance, 0, 0)
                        ON CONFLICT (user_id) DO NOTHING
                        """)
                .param("userId", userId)
                .param("balance", initialBalance)
                .update();
    }

    /**
     * Garante as contas e bloqueia contas e posições existentes dos usuários informados, na ordem
     * determinística. Chamadas posteriores na mesma transação reutilizam esses bloqueios.
     */
    public void lockParticipants(UUID assetId, Collection<UUID> userIds) {
        requireTransaction();
        List<UUID> ordered = userIds.stream().distinct().sorted(LOCK_ORDER).toList();
        ordered.forEach(this::ensureAccount);
        ordered.forEach(this::lockAccount);
        ordered.forEach(userId -> lockPosition(userId, assetId));
    }

    // ---------------------------------------------------------------- reservas

    /**
     * Bloqueia {@code limitPrice × quantity} do saldo disponível para uma ordem de compra.
     * Idempotente por {@code orderId}.
     *
     * @throws InsufficientFundsException se o saldo disponível não cobre o valor.
     */
    public void reserveBuy(UUID orderId, UUID userId, UUID assetId, BigDecimal limitPrice, long quantity) {
        requireTransaction();
        BigDecimal price = price(limitPrice);
        requirePositive(quantity);
        if (sameReservationExists(orderId, userId, assetId, "BUY", price, quantity)) {
            return;
        }

        ensureAccount(userId);
        AccountRow account = lockAccount(userId);
        BigDecimal amount = price.multiply(BigDecimal.valueOf(quantity));
        BigDecimal available = account.balance().subtract(account.reserved());
        if (available.compareTo(amount) < 0) {
            throw new InsufficientFundsException(
                    "Saldo disponível insuficiente: necessário " + amount + ", disponível " + available + ".");
        }

        jdbc.sql("""
                        UPDATE portfolio.accounts
                           SET reserved_balance = reserved_balance + :amount, version = version + 1
                         WHERE user_id = :userId
                        """)
                .param("amount", amount)
                .param("userId", userId)
                .update();
        insertReservation(orderId, userId, assetId, "BUY", price, quantity, amount);
    }

    /**
     * Bloqueia {@code quantity} ações da posição para uma ordem de venda. Idempotente por {@code orderId}.
     *
     * @throws InsufficientPositionException se a quantidade disponível não cobre a venda.
     */
    public void reserveSell(UUID orderId, UUID userId, UUID assetId, BigDecimal limitPrice, long quantity) {
        requireTransaction();
        BigDecimal price = price(limitPrice);
        requirePositive(quantity);
        if (sameReservationExists(orderId, userId, assetId, "SELL", price, quantity)) {
            return;
        }

        ensureAccount(userId);
        lockAccount(userId);
        PositionRow position = lockPosition(userId, assetId)
                .orElseThrow(() -> new InsufficientPositionException("Usuário não possui este ativo."));
        long available = position.quantity() - position.reserved();
        if (available < quantity) {
            throw new InsufficientPositionException(
                    "Quantidade disponível insuficiente: necessário " + quantity + ", disponível " + available + ".");
        }

        jdbc.sql("""
                        UPDATE portfolio.portfolio_positions
                           SET reserved_quantity = reserved_quantity + :quantity, version = version + 1
                         WHERE user_id = :userId AND asset_id = :assetId
                        """)
                .param("quantity", quantity)
                .param("userId", userId)
                .param("assetId", assetId)
                .update();
        insertReservation(orderId, userId, assetId, "SELL", price, quantity, BigDecimal.ZERO);
    }

    /**
     * Devolve o que ainda está reservado para a ordem (cancelamento ou encerramento).
     * Idempotente: em reserva já consumida ou liberada, não altera nada.
     * Ordens sem reserva (legadas da Parte 1) retornam {@code found = false}.
     */
    public ReleaseResult releaseRemaining(UUID orderId) {
        requireTransaction();
        Optional<ReservationRow> found = findReservation(orderId);
        if (found.isEmpty()) {
            return ReleaseResult.notFound(orderId);
        }
        ReservationRow reservation = found.get();
        if (!reservation.active()) {
            return ReleaseResult.alreadyClosed(orderId);
        }

        lockAccount(reservation.userId());
        if (reservation.isBuy()) {
            jdbc.sql("""
                            UPDATE portfolio.accounts
                               SET reserved_balance = reserved_balance - :amount, version = version + 1
                             WHERE user_id = :userId
                            """)
                    .param("amount", reservation.reservedAmount())
                    .param("userId", reservation.userId())
                    .update();
        } else {
            lockPosition(reservation.userId(), reservation.assetId());
            jdbc.sql("""
                            UPDATE portfolio.portfolio_positions
                               SET reserved_quantity = reserved_quantity - :quantity, version = version + 1
                             WHERE user_id = :userId AND asset_id = :assetId
                            """)
                    .param("quantity", reservation.remaining())
                    .param("userId", reservation.userId())
                    .param("assetId", reservation.assetId())
                    .update();
        }

        jdbc.sql("""
                        UPDATE portfolio.order_reservations
                           SET status = 'RELEASED', reserved_amount = 0, updated_at = CURRENT_TIMESTAMP
                         WHERE order_id = :orderId
                        """)
                .param("orderId", orderId)
                .update();
        return new ReleaseResult(orderId, true, true, reservation.reservedAmount(), reservation.remaining());
    }

    // -------------------------------------------------------------- liquidação

    /**
     * Liquida um trade: debita/credita o mesmo valor, transfere a mesma quantidade, atualiza o preço
     * médio do comprador e devolve o excedente da reserva de compra. Idempotente por {@code tradeId}.
     *
     * @throws InvalidSettlementException se o trade não for coerente com as reservas (autonegociação,
     *                                    ativo ou usuário divergente, quantidade acima do restante ou
     *                                    preço fora dos limites das ordens).
     */
    public SettlementResult settleTrade(TradeSettlement trade) {
        requireTransaction();
        BigDecimal price = price(trade.price());
        long quantity = trade.quantity();
        requirePositive(quantity);

        if (alreadySettled(trade.tradeId())) {
            return new SettlementResult(trade.tradeId(), true, BigDecimal.ZERO, BigDecimal.ZERO, false, false);
        }
        if (trade.buyerId().equals(trade.sellerId())) {
            throw new InvalidSettlementException("Autonegociação não é permitida.");
        }

        ReservationRow buy = requireReservation(trade.buyOrderId(), "BUY", trade);
        ReservationRow sell = requireReservation(trade.sellOrderId(), "SELL", trade);
        if (price.compareTo(buy.limitPrice()) > 0) {
            throw new InvalidSettlementException("Preço de execução acima do limite da compra.");
        }
        if (price.compareTo(sell.limitPrice()) < 0) {
            throw new InvalidSettlementException("Preço de execução abaixo do limite da venda.");
        }

        // Bloqueios na ordem determinística. A posição do comprador pode não existir ainda.
        jdbc.sql("""
                        INSERT INTO portfolio.portfolio_positions
                            (id, user_id, asset_id, quantity, reserved_quantity, average_price, version)
                        VALUES (gen_random_uuid(), :userId, :assetId, 0, 0, 0, 0)
                        ON CONFLICT (user_id, asset_id) DO NOTHING
                        """)
                .param("userId", trade.buyerId())
                .param("assetId", trade.assetId())
                .update();
        lockParticipants(trade.assetId(), List.of(trade.buyerId(), trade.sellerId()));
        PositionRow buyerPosition = lockPosition(trade.buyerId(), trade.assetId()).orElseThrow();
        PositionRow sellerPosition = lockPosition(trade.sellerId(), trade.assetId())
                .orElseThrow(() -> new InvalidSettlementException("Vendedor sem posição no ativo."));

        BigDecimal qty = BigDecimal.valueOf(quantity);
        BigDecimal gross = price.multiply(qty);
        BigDecimal buyReservedPortion = buy.limitPrice().multiply(qty);
        BigDecimal excess = buyReservedPortion.subtract(gross);

        // Comprador: paga o valor executado e libera a parte proporcional da reserva (inclui o excedente).
        jdbc.sql("""
                        UPDATE portfolio.accounts
                           SET balance = balance - :gross,
                               reserved_balance = reserved_balance - :reserved,
                               version = version + 1
                         WHERE user_id = :userId
                        """)
                .param("gross", gross)
                .param("reserved", buyReservedPortion)
                .param("userId", trade.buyerId())
                .update();

        // Vendedor: recebe o valor executado.
        jdbc.sql("""
                        UPDATE portfolio.accounts
                           SET balance = balance + :gross, version = version + 1
                         WHERE user_id = :userId
                        """)
                .param("gross", gross)
                .param("userId", trade.sellerId())
                .update();

        // Posição do comprador: soma quantidade e recalcula o preço médio ponderado.
        long newBuyerQuantity = buyerPosition.quantity() + quantity;
        BigDecimal newAverage = buyerPosition.averagePrice()
                .multiply(BigDecimal.valueOf(buyerPosition.quantity()))
                .add(gross)
                .divide(BigDecimal.valueOf(newBuyerQuantity), SCALE, RoundingMode.HALF_EVEN);
        jdbc.sql("""
                        UPDATE portfolio.portfolio_positions
                           SET quantity = :quantity, average_price = :average, version = version + 1
                         WHERE user_id = :userId AND asset_id = :assetId
                        """)
                .param("quantity", newBuyerQuantity)
                .param("average", newAverage)
                .param("userId", trade.buyerId())
                .param("assetId", trade.assetId())
                .update();

        // Posição do vendedor: entrega a quantidade reservada. Preço médio não muda (zera se a posição zerar).
        long newSellerQuantity = sellerPosition.quantity() - quantity;
        jdbc.sql("""
                        UPDATE portfolio.portfolio_positions
                           SET quantity = :quantity,
                               reserved_quantity = reserved_quantity - :sold,
                               average_price = CASE WHEN :quantity = 0 THEN 0 ELSE average_price END,
                               version = version + 1
                         WHERE user_id = :userId AND asset_id = :assetId
                        """)
                .param("quantity", newSellerQuantity)
                .param("sold", quantity)
                .param("userId", trade.sellerId())
                .param("assetId", trade.assetId())
                .update();

        boolean buyFilled = consumeReservation(buy, quantity, buyReservedPortion);
        boolean sellFilled = consumeReservation(sell, quantity, BigDecimal.ZERO);

        jdbc.sql("""
                        INSERT INTO portfolio.trade_settlements
                            (trade_id, asset_id, buy_order_id, sell_order_id, buyer_id, seller_id,
                             price, quantity, gross_amount, buyer_released_excess)
                        VALUES (:tradeId, :assetId, :buyOrderId, :sellOrderId, :buyerId, :sellerId,
                                :price, :quantity, :gross, :excess)
                        """)
                .param("tradeId", trade.tradeId())
                .param("assetId", trade.assetId())
                .param("buyOrderId", trade.buyOrderId())
                .param("sellOrderId", trade.sellOrderId())
                .param("buyerId", trade.buyerId())
                .param("sellerId", trade.sellerId())
                .param("price", price)
                .param("quantity", quantity)
                .param("gross", gross)
                .param("excess", excess)
                .update();

        return new SettlementResult(trade.tradeId(), false, gross, excess, buyFilled, sellFilled);
    }

    // ---------------------------------------------------------------- internos

    private boolean consumeReservation(ReservationRow reservation, long quantity, BigDecimal amount) {
        long remaining = reservation.remaining() - quantity;
        boolean filled = remaining == 0;
        jdbc.sql("""
                        UPDATE portfolio.order_reservations
                           SET remaining_quantity = :remaining,
                               reserved_amount = reserved_amount - :amount,
                               status = :status,
                               updated_at = CURRENT_TIMESTAMP
                         WHERE order_id = :orderId
                        """)
                .param("remaining", remaining)
                .param("amount", amount)
                .param("status", filled ? "CONSUMED" : "ACTIVE")
                .param("orderId", reservation.orderId())
                .update();
        return filled;
    }

    private ReservationRow requireReservation(UUID orderId, String side, TradeSettlement trade) {
        ReservationRow reservation = findReservation(orderId)
                .orElseThrow(() -> new InvalidSettlementException("Ordem " + orderId + " sem reserva."));
        UUID expectedUser = "BUY".equals(side) ? trade.buyerId() : trade.sellerId();
        if (!reservation.side().equals(side)
                || !reservation.userId().equals(expectedUser)
                || !reservation.assetId().equals(trade.assetId())) {
            throw new InvalidSettlementException("Reserva da ordem " + orderId + " não corresponde ao trade.");
        }
        if (!reservation.active()) {
            throw new InvalidSettlementException("Reserva da ordem " + orderId + " não está ativa.");
        }
        if (reservation.remaining() < trade.quantity()) {
            throw new InvalidSettlementException("Quantidade do trade excede o restante da ordem " + orderId + ".");
        }
        return reservation;
    }

    private boolean sameReservationExists(UUID orderId, UUID userId, UUID assetId, String side,
                                          BigDecimal price, long quantity) {
        Optional<ReservationRow> existing = findReservation(orderId);
        if (existing.isEmpty()) {
            return false;
        }
        ReservationRow r = existing.get();
        boolean same = r.userId().equals(userId) && r.assetId().equals(assetId) && r.side().equals(side)
                && r.limitPrice().compareTo(price) == 0 && r.original() == quantity;
        if (!same) {
            throw new InvalidSettlementException("Ordem " + orderId + " já possui reserva com outros dados.");
        }
        return true;
    }

    private void insertReservation(UUID orderId, UUID userId, UUID assetId, String side,
                                   BigDecimal price, long quantity, BigDecimal amount) {
        jdbc.sql("""
                        INSERT INTO portfolio.order_reservations
                            (order_id, user_id, asset_id, side, limit_price, original_quantity,
                             remaining_quantity, reserved_amount, status)
                        VALUES (:orderId, :userId, :assetId, :side, :price, :quantity, :quantity, :amount, 'ACTIVE')
                        """)
                .param("orderId", orderId)
                .param("userId", userId)
                .param("assetId", assetId)
                .param("side", side)
                .param("price", price)
                .param("quantity", quantity)
                .param("amount", amount)
                .update();
    }

    private Optional<ReservationRow> findReservation(UUID orderId) {
        return jdbc.sql("""
                        SELECT order_id, user_id, asset_id, side, limit_price, original_quantity,
                               remaining_quantity, reserved_amount, status
                          FROM portfolio.order_reservations
                         WHERE order_id = :orderId
                        """)
                .param("orderId", orderId)
                .query((rs, n) -> new ReservationRow(
                        rs.getObject("order_id", UUID.class),
                        rs.getObject("user_id", UUID.class),
                        rs.getObject("asset_id", UUID.class),
                        rs.getString("side"),
                        rs.getBigDecimal("limit_price"),
                        rs.getLong("original_quantity"),
                        rs.getLong("remaining_quantity"),
                        rs.getBigDecimal("reserved_amount"),
                        rs.getString("status")))
                .optional();
    }

    private boolean alreadySettled(UUID tradeId) {
        return jdbc.sql("SELECT 1 FROM portfolio.trade_settlements WHERE trade_id = :tradeId")
                .param("tradeId", tradeId)
                .query(Integer.class)
                .optional()
                .isPresent();
    }

    private AccountRow lockAccount(UUID userId) {
        return jdbc.sql("""
                        SELECT balance, reserved_balance FROM portfolio.accounts
                         WHERE user_id = :userId FOR UPDATE
                        """)
                .param("userId", userId)
                .query((rs, n) -> new AccountRow(rs.getBigDecimal("balance"), rs.getBigDecimal("reserved_balance")))
                .optional()
                .orElseThrow(() -> new InvalidSettlementException("Conta inexistente para " + userId + "."));
    }

    private Optional<PositionRow> lockPosition(UUID userId, UUID assetId) {
        return jdbc.sql("""
                        SELECT quantity, reserved_quantity, average_price FROM portfolio.portfolio_positions
                         WHERE user_id = :userId AND asset_id = :assetId FOR UPDATE
                        """)
                .param("userId", userId)
                .param("assetId", assetId)
                .query((rs, n) -> new PositionRow(
                        rs.getLong("quantity"), rs.getLong("reserved_quantity"), rs.getBigDecimal("average_price")))
                .optional();
    }

    private static void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("LedgerService exige uma transação ativa do chamador.");
        }
    }

    private static void requirePositive(long quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantidade deve ser positiva.");
        }
    }

    private static BigDecimal price(BigDecimal value) {
        Objects.requireNonNull(value, "price");
        if (value.signum() <= 0) {
            throw new IllegalArgumentException("Preço deve ser positivo.");
        }
        return money(value);
    }

    private static BigDecimal money(BigDecimal value) {
        try {
            return value.setScale(SCALE, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Valor com mais de " + SCALE + " casas decimais: " + value);
        }
    }

    private record AccountRow(BigDecimal balance, BigDecimal reserved) {
    }

    private record PositionRow(long quantity, long reserved, BigDecimal averagePrice) {
    }

    private record ReservationRow(UUID orderId, UUID userId, UUID assetId, String side, BigDecimal limitPrice,
                                  long original, long remaining, BigDecimal reservedAmount, String status) {
        boolean active() {
            return "ACTIVE".equals(status);
        }

        boolean isBuy() {
            return "BUY".equals(side);
        }
    }
}
