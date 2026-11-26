package br.ufs.stockmarket.agent.strategy;

import br.ufs.stockmarket.agent.client.ApiModels.Asset;
import br.ufs.stockmarket.agent.client.ApiModels.NewOrder;
import br.ufs.stockmarket.agent.client.ApiModels.Order;
import br.ufs.stockmarket.agent.config.AgentsProperties.AgentConfig;
import br.ufs.stockmarket.agent.config.AgentsProperties.StrategyType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StrategyTest {

    static final UUID TECH = UUID.randomUUID();

    static AgentConfig config(StrategyType type, String spread) {
        return new AgentConfig("t", "u", "p", type, 1000, 20, 10, 20, new BigDecimal(spread));
    }

    static Asset asset(String current, String bid, String ask) {
        return new Asset(TECH, "TECH3", new BigDecimal(current),
                bid == null ? null : new BigDecimal(bid), ask == null ? null : new BigDecimal(ask));
    }

    static Order open(String side, String price) {
        return new Order(UUID.randomUUID(), TECH, "TECH3", side, new BigDecimal(price), 5, "OPEN",
                OffsetDateTime.now(), 5);
    }

    @Test
    void marketMakerQuotesBothSidesAroundLastPrice() {
        List<NewOrder> orders = new MarketMakerStrategy()
                .decide(config(StrategyType.MARKET_MAKER, "1.0"), List.of(asset("25.00", null, null)), List.of());

        assertThat(orders).hasSize(2);
        NewOrder buy = orders.stream().filter(o -> o.side().equals("BUY")).findFirst().orElseThrow();
        NewOrder sell = orders.stream().filter(o -> o.side().equals("SELL")).findFirst().orElseThrow();
        assertThat(buy.price()).isEqualByComparingTo("24.88");   // 25 × (1 − 0,5%)
        assertThat(sell.price()).isEqualByComparingTo("25.13");  // 25 × (1 + 0,5%)
        assertThat(buy.quantity()).isBetween(1L, 20L);
    }

    @Test
    void marketMakerDoesNotDuplicateOrCrossOwnOrders() {
        MarketMakerStrategy strategy = new MarketMakerStrategy();
        AgentConfig config = config(StrategyType.MARKET_MAKER, "1.0");

        // Já tem compra aberta: só cota a venda.
        assertThat(strategy.decide(config, List.of(asset("25.00", null, null)), List.of(open("BUY", "24.80"))))
                .extracting(NewOrder::side).containsExactly("SELL");

        // Venda própria antiga a 24,50 cruzaria com a nova compra a 24,88: não cota a compra.
        assertThat(strategy.decide(config, List.of(asset("25.00", null, null)), List.of(open("SELL", "24.50"))))
                .isEmpty();
    }

    @Test
    void momentumFollowsPriceDirectionAndHitsTheBook() {
        MomentumStrategy strategy = new MomentumStrategy();
        AgentConfig config = config(StrategyType.MOMENTUM, "0");

        strategy.decide(config, List.of(asset("25.00", "24.90", "25.10")), List.of()); // primeira observação
        List<NewOrder> up = strategy.decide(config, List.of(asset("25.50", "25.40", "25.60")), List.of());
        assertThat(up).singleElement().satisfies(o -> {
            assertThat(o.side()).isEqualTo("BUY");
            assertThat(o.price()).isEqualByComparingTo("25.60"); // agride a melhor venda
        });

        List<NewOrder> down = strategy.decide(config, List.of(asset("25.00", "24.90", "25.10")), List.of());
        assertThat(down).singleElement().satisfies(o -> {
            assertThat(o.side()).isEqualTo("SELL");
            assertThat(o.price()).isEqualByComparingTo("24.90"); // agride a melhor compra
        });
    }

    @Test
    void momentumSkipsWhenThereIsNothingToHit() {
        MomentumStrategy strategy = new MomentumStrategy();
        AgentConfig config = config(StrategyType.MOMENTUM, "0");
        assertThat(strategy.decide(config, List.of(asset("25.00", null, null)), List.of())).isEmpty();
    }
}
