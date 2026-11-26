package br.ufs.stockmarket.agent.strategy;

import br.ufs.stockmarket.agent.client.ApiModels.Asset;
import br.ufs.stockmarket.agent.client.ApiModels.NewOrder;
import br.ufs.stockmarket.agent.client.ApiModels.Order;
import br.ufs.stockmarket.agent.config.AgentsProperties.AgentConfig;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Tomador de liquidez: escolhe um ativo ao acaso e segue o último movimento de preço.
 * Subiu desde a última observação: compra no melhor preço de venda. Caiu: vende no melhor preço de compra.
 * Ficou igual: lado aleatório. Como agride o livro, gera trades com quem está cotando (ex.: market maker).
 */
public class MomentumStrategy implements Strategy {

    private final Map<UUID, BigDecimal> lastSeen = new HashMap<>();

    @Override
    public List<NewOrder> decide(AgentConfig config, List<Asset> assets, List<Order> openOrders) {
        if (assets.isEmpty()) {
            return List.of();
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Asset asset = assets.get(random.nextInt(assets.size()));
        BigDecimal previous = lastSeen.put(asset.id(), asset.currentPrice());

        int direction = previous == null ? 0 : asset.currentPrice().compareTo(previous);
        String side = direction > 0 ? "BUY" : direction < 0 ? "SELL" : (random.nextBoolean() ? "BUY" : "SELL");
        BigDecimal price = "BUY".equals(side) ? asset.bestAsk() : asset.bestBid();
        if (price == null) {
            return List.of(); // não há oferta do outro lado do livro para agredir
        }
        long quantity = random.nextLong(1, config.maxOrderQuantity() + 1);
        return List.of(new NewOrder(asset.id(), side, Prices.tick(price), quantity));
    }
}
