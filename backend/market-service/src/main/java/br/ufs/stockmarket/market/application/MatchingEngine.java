package br.ufs.stockmarket.market.application;

import br.ufs.stockmarket.market.domain.OrderSide;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Planejador puro de negociacoes. Nao altera entidades, banco ou saldo.
 * O chamador deve manter o bloqueio do ativo durante planejamento e execucao.
 */
public final class MatchingEngine {

    public record OrderView(UUID id, UUID userId, UUID assetId, OrderSide side,
                            BigDecimal price, long remainingQuantity, long sequenceNumber) {
        public OrderView {
            Objects.requireNonNull(id);
            Objects.requireNonNull(userId);
            Objects.requireNonNull(assetId);
            Objects.requireNonNull(side);
            Objects.requireNonNull(price);
            if (price.signum() <= 0 || remainingQuantity <= 0 || sequenceNumber < 0) {
                throw new IllegalArgumentException("Ordem invalida para matching.");
            }
        }
    }

    public record Match(UUID makerOrderId, BigDecimal price, long quantity) {}

    public record Plan(List<Match> matches, long remainingQuantity) {
        public Plan {
            matches = List.copyOf(matches);
        }
    }

    public static final class SelfTradeException extends IllegalArgumentException {
        public SelfTradeException() {
            super("A ordem cruzaria com uma ordem do mesmo investidor.");
        }
    }

    /**
     * Recebe a ordem nova (taker) e apenas ordens ativas do livro (makers).
     * Sequencia zero e permitida somente para o taker ainda nao persistido.
     * Se houver autonegociacao durante o cruzamento, rejeita o plano inteiro.
     */
    public Plan plan(OrderView taker, List<OrderView> candidates) {
        Objects.requireNonNull(taker);
        Objects.requireNonNull(candidates);

        Comparator<OrderView> pricePriority = Comparator.comparing(OrderView::price);
        if (taker.side() == OrderSide.SELL) {
            pricePriority = pricePriority.reversed();
        }
        Comparator<OrderView> priority = pricePriority.thenComparingLong(OrderView::sequenceNumber);

        List<OrderView> eligible = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (OrderView maker : candidates) {
            Objects.requireNonNull(maker);
            if (maker.id().equals(taker.id()) || !maker.assetId().equals(taker.assetId())
                    || maker.side() == taker.side() || !crosses(taker, maker)) {
                continue;
            }
            if (maker.sequenceNumber() <= 0) {
                throw new IllegalArgumentException("Ordem passiva sem sequencia valida.");
            }
            if (!seen.add(maker.id())) {
                throw new IllegalArgumentException("Ordem passiva duplicada no livro.");
            }
            eligible.add(maker);
        }
        eligible.sort(priority);

        long remaining = taker.remainingQuantity();
        List<Match> matches = new ArrayList<>();
        for (OrderView maker : eligible) {
            if (remaining == 0) {
                break;
            }
            if (maker.userId().equals(taker.userId())) {
                throw new SelfTradeException();
            }
            long quantity = Math.min(remaining, maker.remainingQuantity());
            matches.add(new Match(maker.id(), maker.price(), quantity));
            remaining -= quantity;
        }
        return new Plan(matches, remaining);
    }

    private boolean crosses(OrderView taker, OrderView maker) {
        int comparison = taker.price().compareTo(maker.price());
        return taker.side() == OrderSide.BUY ? comparison >= 0 : comparison <= 0;
    }
}
