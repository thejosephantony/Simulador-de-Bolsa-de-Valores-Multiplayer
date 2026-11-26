package br.ufs.stockmarket.agent.strategy;

import br.ufs.stockmarket.agent.client.ApiModels.Asset;
import br.ufs.stockmarket.agent.client.ApiModels.NewOrder;
import br.ufs.stockmarket.agent.client.ApiModels.Order;
import br.ufs.stockmarket.agent.config.AgentsProperties.AgentConfig;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Provedor de liquidez: mantém uma compra abaixo e uma venda acima do último preço de cada ativo,
 * separadas por {@code spreadPercent}. Cada rodada cuida de um ativo (rodízio) para limitar a taxa de ordens,
 * e só cota um lado se ainda não houver ordem própria aberta naquele lado e ativo.
 */
public class MarketMakerStrategy implements Strategy {

    private static final BigDecimal TWO_HUNDRED = BigDecimal.valueOf(200);
    private int cursor;

    @Override
    public List<NewOrder> decide(AgentConfig config, List<Asset> assets, List<Order> openOrders) {
        if (assets.isEmpty()) {
            return List.of();
        }
        Asset asset = assets.get(Math.floorMod(cursor++, assets.size()));
        BigDecimal half = config.spreadPercent().divide(TWO_HUNDRED, 6, RoundingMode.HALF_UP);
        BigDecimal reference = asset.currentPrice();
        BigDecimal bid = Prices.tick(reference.multiply(BigDecimal.ONE.subtract(half)));
        BigDecimal ask = Prices.tick(reference.multiply(BigDecimal.ONE.add(half)));
        if (bid.compareTo(ask) >= 0) {
            ask = bid.add(Prices.TICK);
        }
        long quantity = ThreadLocalRandom.current().nextLong(1, config.maxOrderQuantity() + 1);

        List<NewOrder> orders = new ArrayList<>();
        // Não cotar um lado que cruzaria com a própria ordem do outro lado (autonegociação é rejeitada).
        if (!hasOpen(openOrders, asset, "BUY") && !ownAskAtOrBelow(openOrders, asset, bid)) {
            orders.add(new NewOrder(asset.id(), "BUY", bid, quantity));
        }
        if (!hasOpen(openOrders, asset, "SELL") && !ownBidAtOrAbove(openOrders, asset, ask)) {
            orders.add(new NewOrder(asset.id(), "SELL", ask, quantity));
        }
        return orders;
    }

    private static boolean hasOpen(List<Order> openOrders, Asset asset, String side) {
        return openOrders.stream().anyMatch(o -> o.assetId().equals(asset.id()) && o.side().equals(side));
    }

    private static boolean ownAskAtOrBelow(List<Order> openOrders, Asset asset, BigDecimal bid) {
        return openOrders.stream().anyMatch(o -> o.assetId().equals(asset.id()) && "SELL".equals(o.side())
                && o.price().compareTo(bid) <= 0);
    }

    private static boolean ownBidAtOrAbove(List<Order> openOrders, Asset asset, BigDecimal ask) {
        return openOrders.stream().anyMatch(o -> o.assetId().equals(asset.id()) && "BUY".equals(o.side())
                && o.price().compareTo(ask) >= 0);
    }
}
