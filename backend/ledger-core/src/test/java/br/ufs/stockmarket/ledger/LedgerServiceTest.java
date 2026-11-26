package br.ufs.stockmarket.ledger;

import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testes contra PostgreSQL real, usando as migrations do portfolio-service.
 * Requer Docker em execução; sem Docker os testes são ignorados.
 */
@Testcontainers(disabledWithoutDocker = true)
class LedgerServiceTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    static HikariDataSource dataSource;
    static JdbcClient jdbc;
    static TransactionTemplate tx;
    static LedgerService ledger;

    static final UUID ASSET = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @BeforeAll
    static void setUp() {
        dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(POSTGRES.getJdbcUrl());
        dataSource.setUsername(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        dataSource.setMaximumPoolSize(30);

        Path migrations = Path.of("..", "portfolio-service", "src", "main", "resources", "db", "migration")
                .toAbsolutePath().normalize();
        Flyway.configure()
                .dataSource(dataSource)
                .schemas("portfolio")
                .createSchemas(true)
                .locations("filesystem:" + migrations)
                .load()
                .migrate();

        jdbc = JdbcClient.create(dataSource);
        tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        ledger = new LedgerService(jdbc, new BigDecimal("100000.00"));
    }

    @AfterAll
    static void tearDown() {
        dataSource.close();
    }

    @BeforeEach
    void clean() {
        jdbc.sql("TRUNCATE portfolio.trade_settlements, portfolio.order_reservations, "
                + "portfolio.portfolio_positions, portfolio.accounts").update();
    }

    // ------------------------------------------------------------------ helpers

    private void inTx(Runnable action) {
        tx.executeWithoutResult(status -> action.run());
    }

    private <T> T inTx(java.util.function.Supplier<T> action) {
        return tx.execute(status -> action.get());
    }

    private UUID userWithShares(long shares, String averagePrice) {
        UUID user = UUID.randomUUID();
        inTx(() -> ledger.ensureAccount(user));
        jdbc.sql("""
                        INSERT INTO portfolio.portfolio_positions
                            (id, user_id, asset_id, quantity, reserved_quantity, average_price, version)
                        VALUES (gen_random_uuid(), :u, :a, :q, 0, :p, 0)
                        """)
                .param("u", user).param("a", ASSET).param("q", shares).param("p", new BigDecimal(averagePrice))
                .update();
        return user;
    }

    private UUID userWithCash() {
        UUID user = UUID.randomUUID();
        inTx(() -> ledger.ensureAccount(user));
        return user;
    }

    private BigDecimal balance(UUID user) {
        return jdbc.sql("SELECT balance FROM portfolio.accounts WHERE user_id = :u")
                .param("u", user).query(BigDecimal.class).single();
    }

    private BigDecimal reservedBalance(UUID user) {
        return jdbc.sql("SELECT reserved_balance FROM portfolio.accounts WHERE user_id = :u")
                .param("u", user).query(BigDecimal.class).single();
    }

    private long quantity(UUID user) {
        return jdbc.sql("SELECT quantity FROM portfolio.portfolio_positions WHERE user_id = :u AND asset_id = :a")
                .param("u", user).param("a", ASSET).query(Long.class).optional().orElse(0L);
    }

    private long reservedQuantity(UUID user) {
        return jdbc.sql("SELECT reserved_quantity FROM portfolio.portfolio_positions WHERE user_id = :u AND asset_id = :a")
                .param("u", user).param("a", ASSET).query(Long.class).optional().orElse(0L);
    }

    private BigDecimal averagePrice(UUID user) {
        return jdbc.sql("SELECT average_price FROM portfolio.portfolio_positions WHERE user_id = :u AND asset_id = :a")
                .param("u", user).param("a", ASSET).query(BigDecimal.class).single();
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value).setScale(4);
    }

    // -------------------------------------------------------------------- contas

    @Test
    void ensureAccountCreatesOnceWithInitialBalance() {
        UUID user = UUID.randomUUID();
        inTx(() -> ledger.ensureAccount(user));
        inTx(() -> ledger.ensureAccount(user));

        assertThat(balance(user)).isEqualByComparingTo("100000.00");
        assertThat(jdbc.sql("SELECT count(*) FROM portfolio.accounts WHERE user_id = :u")
                .param("u", user).query(Long.class).single()).isEqualTo(1);
    }

    @Test
    void operationsRequireCallerTransaction() {
        assertThatThrownBy(() -> ledger.ensureAccount(UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class);
    }

    // ------------------------------------------------------------------- reservas

    @Test
    void reserveBuyBlocksLimitTimesQuantity() {
        UUID buyer = userWithCash();
        inTx(() -> ledger.reserveBuy(UUID.randomUUID(), buyer, ASSET, new BigDecimal("25.50"), 100));

        assertThat(reservedBalance(buyer)).isEqualByComparingTo("2550.00");
        assertThat(balance(buyer)).isEqualByComparingTo("100000.00");
    }

    @Test
    void reserveBuyRejectsWhenAvailableIsInsufficient() {
        UUID buyer = userWithCash();
        inTx(() -> ledger.reserveBuy(UUID.randomUUID(), buyer, ASSET, new BigDecimal("1000"), 90));

        assertThatThrownBy(() -> inTx(() ->
                ledger.reserveBuy(UUID.randomUUID(), buyer, ASSET, new BigDecimal("1000"), 11)))
                .isInstanceOf(InsufficientFundsException.class);
        assertThat(reservedBalance(buyer)).isEqualByComparingTo("90000.00");
    }

    @Test
    void reserveIsIdempotentPerOrder() {
        UUID buyer = userWithCash();
        UUID order = UUID.randomUUID();
        inTx(() -> ledger.reserveBuy(order, buyer, ASSET, new BigDecimal("10"), 10));
        inTx(() -> ledger.reserveBuy(order, buyer, ASSET, new BigDecimal("10"), 10));

        assertThat(reservedBalance(buyer)).isEqualByComparingTo("100.00");
        assertThatThrownBy(() -> inTx(() -> ledger.reserveBuy(order, buyer, ASSET, new BigDecimal("11"), 10)))
                .isInstanceOf(InvalidSettlementException.class);
    }

    @Test
    void reserveSellRejectsShortSelling() {
        UUID noShares = userWithCash();
        assertThatThrownBy(() -> inTx(() ->
                ledger.reserveSell(UUID.randomUUID(), noShares, ASSET, new BigDecimal("10"), 1)))
                .isInstanceOf(InsufficientPositionException.class);

        UUID seller = userWithShares(50, "20");
        inTx(() -> ledger.reserveSell(UUID.randomUUID(), seller, ASSET, new BigDecimal("10"), 30));
        assertThatThrownBy(() -> inTx(() ->
                ledger.reserveSell(UUID.randomUUID(), seller, ASSET, new BigDecimal("10"), 21)))
                .isInstanceOf(InsufficientPositionException.class);
        assertThat(reservedQuantity(seller)).isEqualTo(30);
    }

    @Test
    void releaseReturnsReservationAndIsIdempotent() {
        UUID buyer = userWithCash();
        UUID seller = userWithShares(10, "20");
        UUID buyOrder = UUID.randomUUID();
        UUID sellOrder = UUID.randomUUID();
        inTx(() -> {
            ledger.reserveBuy(buyOrder, buyer, ASSET, new BigDecimal("30"), 10);
            ledger.reserveSell(sellOrder, seller, ASSET, new BigDecimal("30"), 10);
        });

        ReleaseResult first = inTx(() -> ledger.releaseRemaining(buyOrder));
        ReleaseResult second = inTx(() -> ledger.releaseRemaining(buyOrder));
        inTx(() -> ledger.releaseRemaining(sellOrder));

        assertThat(first.released()).isTrue();
        assertThat(first.releasedAmount()).isEqualByComparingTo("300");
        assertThat(second.released()).isFalse();
        assertThat(reservedBalance(buyer)).isEqualByComparingTo("0");
        assertThat(reservedQuantity(seller)).isZero();
    }

    @Test
    void releaseOfLegacyOrderWithoutReservationIsNoOp() {
        ReleaseResult result = inTx(() -> ledger.releaseRemaining(UUID.randomUUID()));
        assertThat(result.found()).isFalse();
    }

    // ----------------------------------------------------------------- liquidação

    @Test
    void fullSettlementMovesCashAndSharesAndReleasesExcess() {
        UUID buyer = userWithCash();
        UUID seller = userWithShares(100, "20");
        UUID buyOrder = UUID.randomUUID();
        UUID sellOrder = UUID.randomUUID();
        inTx(() -> {
            ledger.reserveSell(sellOrder, seller, ASSET, new BigDecimal("25"), 100);
            ledger.reserveBuy(buyOrder, buyer, ASSET, new BigDecimal("30"), 100);
        });

        // Executa ao preço da ordem passiva (venda, 25), abaixo do limite da compra (30).
        SettlementResult result = inTx(() -> ledger.settleTrade(new TradeSettlement(
                UUID.randomUUID(), ASSET, buyOrder, sellOrder, buyer, seller, new BigDecimal("25"), 100)));

        assertThat(result.grossAmount()).isEqualByComparingTo("2500");
        assertThat(result.buyerReleasedExcess()).isEqualByComparingTo("500");
        assertThat(result.buyOrderFilled()).isTrue();
        assertThat(result.sellOrderFilled()).isTrue();

        assertThat(balance(buyer)).isEqualByComparingTo("97500");
        assertThat(reservedBalance(buyer)).isEqualByComparingTo("0");
        assertThat(quantity(buyer)).isEqualTo(100);
        assertThat(averagePrice(buyer)).isEqualByComparingTo("25");

        assertThat(balance(seller)).isEqualByComparingTo("102500");
        assertThat(quantity(seller)).isZero();
        assertThat(reservedQuantity(seller)).isZero();
    }

    @Test
    void partialSettlementsThenCancelReleaseOnlyTheRest() {
        UUID buyer = userWithCash();
        UUID sellerA = userWithShares(30, "20");
        UUID sellerB = userWithShares(30, "20");
        UUID buyOrder = UUID.randomUUID();
        UUID sellA = UUID.randomUUID();
        UUID sellB = UUID.randomUUID();
        inTx(() -> {
            ledger.reserveSell(sellA, sellerA, ASSET, new BigDecimal("20"), 30);
            ledger.reserveSell(sellB, sellerB, ASSET, new BigDecimal("22"), 30);
            ledger.reserveBuy(buyOrder, buyer, ASSET, new BigDecimal("25"), 100);
        });

        // Dois matches na mesma transação, com bloqueio prévio dos participantes.
        inTx(() -> {
            ledger.lockParticipants(ASSET, List.of(buyer, sellerA, sellerB));
            ledger.settleTrade(new TradeSettlement(UUID.randomUUID(), ASSET, buyOrder, sellA, buyer, sellerA,
                    new BigDecimal("20"), 30));
            ledger.settleTrade(new TradeSettlement(UUID.randomUUID(), ASSET, buyOrder, sellB, buyer, sellerB,
                    new BigDecimal("22"), 30));
        });

        // Pagou 600 + 660; ainda reserva 40 × 25 = 1000.
        assertThat(balance(buyer)).isEqualByComparingTo("98740");
        assertThat(reservedBalance(buyer)).isEqualByComparingTo("1000");
        assertThat(quantity(buyer)).isEqualTo(60);
        assertThat(averagePrice(buyer)).isEqualByComparingTo("21");

        ReleaseResult release = inTx(() -> ledger.releaseRemaining(buyOrder));
        assertThat(release.releasedQuantity()).isEqualTo(40);
        assertThat(release.releasedAmount()).isEqualByComparingTo("1000");
        assertThat(reservedBalance(buyer)).isEqualByComparingTo("0");
    }

    @Test
    void averagePriceIsWeightedAcrossTrades() {
        UUID buyer = userWithShares(100, "10");
        UUID seller = userWithShares(50, "15");
        UUID buyOrder = UUID.randomUUID();
        UUID sellOrder = UUID.randomUUID();
        inTx(() -> {
            ledger.reserveSell(sellOrder, seller, ASSET, new BigDecimal("13"), 50);
            ledger.reserveBuy(buyOrder, buyer, ASSET, new BigDecimal("13"), 50);
            ledger.settleTrade(new TradeSettlement(UUID.randomUUID(), ASSET, buyOrder, sellOrder, buyer, seller,
                    new BigDecimal("13"), 50));
        });

        // (100 × 10 + 50 × 13) / 150 = 11
        assertThat(averagePrice(buyer)).isEqualByComparingTo("11");
        assertThat(averagePrice(seller)).isEqualByComparingTo("0");
    }

    @Test
    void settlementIsIdempotentPerTrade() {
        UUID buyer = userWithCash();
        UUID seller = userWithShares(100, "20");
        UUID buyOrder = UUID.randomUUID();
        UUID sellOrder = UUID.randomUUID();
        UUID tradeId = UUID.randomUUID();
        inTx(() -> {
            ledger.reserveSell(sellOrder, seller, ASSET, new BigDecimal("25"), 100);
            ledger.reserveBuy(buyOrder, buyer, ASSET, new BigDecimal("25"), 100);
        });
        TradeSettlement trade = new TradeSettlement(tradeId, ASSET, buyOrder, sellOrder, buyer, seller,
                new BigDecimal("25"), 40);

        inTx(() -> ledger.settleTrade(trade));
        SettlementResult again = inTx(() -> ledger.settleTrade(trade));

        assertThat(again.duplicate()).isTrue();
        assertThat(quantity(buyer)).isEqualTo(40);
        assertThat(balance(seller)).isEqualByComparingTo("101000");
    }

    @Test
    void rejectsSelfTradeAndPriceOutsideLimits() {
        UUID user = userWithShares(10, "20");
        UUID other = userWithShares(10, "20");
        UUID buyOrder = UUID.randomUUID();
        UUID sellOrder = UUID.randomUUID();
        UUID otherSell = UUID.randomUUID();
        inTx(() -> {
            ledger.reserveBuy(buyOrder, user, ASSET, new BigDecimal("20"), 5);
            ledger.reserveSell(sellOrder, user, ASSET, new BigDecimal("20"), 5);
            ledger.reserveSell(otherSell, other, ASSET, new BigDecimal("20"), 5);
        });

        assertThatThrownBy(() -> inTx(() -> ledger.settleTrade(new TradeSettlement(
                UUID.randomUUID(), ASSET, buyOrder, sellOrder, user, user, new BigDecimal("20"), 5))))
                .isInstanceOf(InvalidSettlementException.class);
        assertThatThrownBy(() -> inTx(() -> ledger.settleTrade(new TradeSettlement(
                UUID.randomUUID(), ASSET, buyOrder, otherSell, user, other, new BigDecimal("21"), 5))))
                .isInstanceOf(InvalidSettlementException.class);
    }

    @Test
    void failureInCallerTransactionRollsBackEverything() {
        UUID buyer = userWithCash();
        UUID seller = userWithShares(10, "20");
        UUID buyOrder = UUID.randomUUID();
        UUID sellOrder = UUID.randomUUID();

        assertThatThrownBy(() -> inTx(() -> {
            ledger.reserveSell(sellOrder, seller, ASSET, new BigDecimal("20"), 10);
            ledger.reserveBuy(buyOrder, buyer, ASSET, new BigDecimal("20"), 10);
            ledger.settleTrade(new TradeSettlement(UUID.randomUUID(), ASSET, buyOrder, sellOrder, buyer, seller,
                    new BigDecimal("20"), 10));
            throw new IllegalStateException("falha simulada após a liquidação (ex.: erro ao gravar outbox)");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(balance(buyer)).isEqualByComparingTo("100000");
        assertThat(reservedBalance(buyer)).isEqualByComparingTo("0");
        assertThat(quantity(seller)).isEqualTo(10);
        assertThat(quantity(buyer)).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM portfolio.order_reservations").query(Long.class).single())
                .isZero();
    }

    // ---------------------------------------------------------------- concorrência

    @Test
    void concurrentReservationsNeverOverdrawBalance() throws Exception {
        UUID buyer = userWithCash(); // 100 000
        int threads = 25;            // 25 × 10 000 = 250 000 pedidos; só 10 cabem
        AtomicInteger accepted = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();

        runConcurrently(threads, () -> {
            try {
                inTx(() -> ledger.reserveBuy(UUID.randomUUID(), buyer, ASSET, new BigDecimal("100"), 100));
                accepted.incrementAndGet();
            } catch (InsufficientFundsException e) {
                rejected.incrementAndGet();
            }
            return null;
        });

        assertThat(accepted.get()).isEqualTo(10);
        assertThat(rejected.get()).isEqualTo(15);
        assertThat(reservedBalance(buyer)).isEqualByComparingTo("100000");
    }

    @Test
    void concurrentSellReservationsNeverExceedPosition() throws Exception {
        UUID seller = userWithShares(100, "20");
        AtomicInteger accepted = new AtomicInteger();

        runConcurrently(20, () -> {
            try {
                inTx(() -> ledger.reserveSell(UUID.randomUUID(), seller, ASSET, new BigDecimal("20"), 10));
                accepted.incrementAndGet();
            } catch (InsufficientPositionException ignored) {
                // esperado para as que excedem
            }
            return null;
        });

        assertThat(accepted.get()).isEqualTo(10);
        assertThat(reservedQuantity(seller)).isEqualTo(100);
    }

    @Test
    void concurrentAccountCreationForSameUserDoesNotFail() throws Exception {
        UUID user = UUID.randomUUID();
        runConcurrently(20, () -> {
            inTx(() -> ledger.ensureAccount(user));
            return null;
        });

        assertThat(jdbc.sql("SELECT count(*) FROM portfolio.accounts WHERE user_id = :u")
                .param("u", user).query(Long.class).single()).isEqualTo(1);
    }

    private static void runConcurrently(int threads, Callable<Void> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Void>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return task.call();
            }));
        }
        start.countDown();
        for (Future<Void> future : futures) {
            future.get(); // propaga qualquer exceção inesperada
        }
        pool.shutdown();
    }
}
