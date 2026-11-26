package br.ufs.stockmarket.market.application;

import br.ufs.stockmarket.market.domain.OrderSide;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static br.ufs.stockmarket.market.domain.OrderSide.BUY;
import static br.ufs.stockmarket.market.domain.OrderSide.SELL;
import static org.junit.jupiter.api.Assertions.*;

class MatchingEngineTest {
    private final MatchingEngine engine = new MatchingEngine();
    private final UUID asset = UUID.randomUUID();
    private final UUID takerUser = UUID.randomUUID();

    private MatchingEngine.OrderView taker(OrderSide side, String price, long quantity) {
        return new MatchingEngine.OrderView(UUID.randomUUID(), takerUser, asset, side,
                new BigDecimal(price), quantity, 0);
    }

    private MatchingEngine.OrderView maker(OrderSide side, String price, long quantity, long sequence) {
        return new MatchingEngine.OrderView(UUID.randomUUID(), UUID.randomUUID(), asset, side,
                new BigDecimal(price), quantity, sequence);
    }

    @Test
    void buyUsesLowestAskAndPassivePrice() {
        var incoming = taker(BUY, "30", 10);
        var expensive = maker(SELL, "25", 10, 1);
        var cheap = maker(SELL, "20", 10, 2);
        var plan = engine.plan(incoming, List.of(expensive, cheap));
        assertEquals(List.of(new MatchingEngine.Match(cheap.id(), new BigDecimal("20"), 10)), plan.matches());
        assertEquals(0, plan.remainingQuantity());
    }

    @Test
    void sellUsesHighestBidAndPassivePrice() {
        var incoming = taker(SELL, "20", 10);
        var low = maker(BUY, "21", 10, 1);
        var high = maker(BUY, "25", 10, 2);
        var plan = engine.plan(incoming, List.of(low, high));
        assertEquals(List.of(new MatchingEngine.Match(high.id(), new BigDecimal("25"), 10)), plan.matches());
    }

    @Test
    void equalPricesUseEarliestSequenceForBothSides() {
        for (OrderSide side : List.of(BUY, SELL)) {
            OrderSide opposite = side == BUY ? SELL : BUY;
            var old = maker(opposite, "25.00", 5, 1);
            var recent = maker(opposite, "25.0", 5, 2);
            var plan = engine.plan(taker(side, "25", 5), List.of(recent, old));
            assertEquals(old.id(), plan.matches().getFirst().makerOrderId());
        }
    }

    @Test
    void partialFillLeavesRemainder() {
        var plan = engine.plan(taker(BUY, "30", 100), List.of(maker(SELL, "25", 40, 1)));
        assertEquals(40, plan.matches().getFirst().quantity());
        assertEquals(60, plan.remainingQuantity());
    }

    @Test
    void matchesMultipleOrdersWithoutExceedingQuantity() {
        var a = maker(SELL, "20", 30, 1);
        var b = maker(SELL, "21", 50, 2);
        var plan = engine.plan(taker(BUY, "22", 60), List.of(b, a));
        assertEquals(List.of(new MatchingEngine.Match(a.id(), a.price(), 30),
                new MatchingEngine.Match(b.id(), b.price(), 30)), plan.matches());
        assertEquals(0, plan.remainingQuantity());
        assertEquals(50, b.remainingQuantity()); // planejamento nao muta a ordem passiva
    }

    @Test
    void incompatiblePricesDoNotTrade() {
        assertTrue(engine.plan(taker(BUY, "20", 10), List.of(maker(SELL, "21", 10, 1))).matches().isEmpty());
        assertTrue(engine.plan(taker(SELL, "20", 10), List.of(maker(BUY, "19", 10, 1))).matches().isEmpty());
    }

    @Test
    void ignoresOtherAssetsAndSameSide() {
        var otherAsset = new MatchingEngine.OrderView(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                SELL, new BigDecimal("10"), 100, 1);
        var plan = engine.plan(taker(BUY, "20", 10), List.of(otherAsset, maker(BUY, "20", 10, 2)));
        assertTrue(plan.matches().isEmpty());
        assertEquals(10, plan.remainingQuantity());
    }

    @Test
    void selfTradeRejectsWholePlanEvenAfterOtherCandidate() {
        var external = maker(SELL, "20", 5, 1);
        var own = new MatchingEngine.OrderView(UUID.randomUUID(), takerUser, asset, SELL,
                new BigDecimal("21"), 5, 2);
        assertThrows(MatchingEngine.SelfTradeException.class,
                () -> engine.plan(taker(BUY, "22", 10), List.of(own, external)));
    }

    @Test
    void selfOrderNotReachedDoesNotReject() {
        var external = maker(SELL, "20", 10, 1);
        var own = new MatchingEngine.OrderView(UUID.randomUUID(), takerUser, asset, SELL,
                new BigDecimal("21"), 5, 2);
        assertEquals(0, engine.plan(taker(BUY, "22", 10), List.of(own, external)).remainingQuantity());
    }

    @Test
    void rejectsDuplicateCandidatesAndInvalidSequence() {
        var incoming = taker(BUY, "22", 10);
        var passive = maker(SELL, "20", 5, 1);
        assertThrows(IllegalArgumentException.class, () -> engine.plan(incoming, List.of(passive, passive)));
        assertThrows(IllegalArgumentException.class, () -> engine.plan(incoming, List.of(maker(SELL, "20", 5, 0))));
    }
}
