package br.ufs.stockmarket.agent.engine;

import br.ufs.stockmarket.agent.client.ApiModels.Asset;
import br.ufs.stockmarket.agent.client.ApiModels.NewOrder;
import br.ufs.stockmarket.agent.client.ApiModels.Order;
import br.ufs.stockmarket.agent.client.ApiModels.Portfolio;
import br.ufs.stockmarket.agent.client.TradingClient;
import br.ufs.stockmarket.agent.config.AgentsProperties.AgentConfig;
import br.ufs.stockmarket.agent.strategy.Strategy;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.HttpStatusCodeException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Executa um agente: a cada intervalo, lê o mercado e a própria carteira pela API, cancela ordens velhas,
 * pede à estratégia novas ordens e as envia respeitando os limites configurados.
 */
public class AgentRunner {

    private static final Logger log = LoggerFactory.getLogger(AgentRunner.class);

    private final AgentConfig config;
    private final TradingClient client;
    private final Strategy strategy;
    private final MeterRegistry metrics;
    private final AtomicBoolean active;

    private volatile Instant nextRunAt = Instant.EPOCH;
    private volatile Instant lastRunAt;
    private volatile String lastError;
    private final AtomicLong submitted = new AtomicLong();
    private final AtomicLong rejected = new AtomicLong();
    private final AtomicLong cancelled = new AtomicLong();

    public AgentRunner(AgentConfig config, TradingClient client, Strategy strategy, MeterRegistry metrics,
                       boolean startActive) {
        this.config = config;
        this.client = client;
        this.strategy = strategy;
        this.metrics = metrics;
        this.active = new AtomicBoolean(startActive);
        metrics.gauge("agent.active", List.of(io.micrometer.core.instrument.Tag.of("agent", config.name())),
                active, flag -> flag.get() ? 1 : 0);
    }

    public boolean due(Instant now) {
        return active.get() && !now.isBefore(nextRunAt);
    }

    public void runOnce() {
        nextRunAt = Instant.now().plusMillis(config.intervalMs());
        lastRunAt = Instant.now();
        try {
            List<Asset> assets = client.assets();
            List<Order> open = new ArrayList<>(client.myOrders().stream().filter(Order::isOpen).toList());

            cancelStale(open);
            if (open.size() >= config.maxOpenOrders()) {
                count("agent.orders.skipped", "reason", "max_open_orders");
                return;
            }

            Portfolio portfolio = client.myPortfolio();
            BigDecimal cash = portfolio.availableBalance();
            Map<UUID, Long> shares = new HashMap<>();
            int openCount = open.size();

            for (NewOrder order : strategy.decide(config, assets, open)) {
                if (openCount >= config.maxOpenOrders()) {
                    count("agent.orders.skipped", "reason", "max_open_orders");
                    break;
                }
                long quantity = Math.min(order.quantity(), config.maxOrderQuantity());
                NewOrder limited = new NewOrder(order.assetId(), order.side(), order.price(), quantity);

                if ("BUY".equals(limited.side())) {
                    BigDecimal notional = limited.price().multiply(BigDecimal.valueOf(quantity));
                    if (cash.compareTo(notional) < 0) {
                        count("agent.orders.skipped", "reason", "insufficient_cash");
                        continue;
                    }
                    cash = cash.subtract(notional);
                } else {
                    long available = shares.computeIfAbsent(limited.assetId(), portfolio::available);
                    if (available < quantity) {
                        count("agent.orders.skipped", "reason", "insufficient_shares");
                        continue;
                    }
                    shares.put(limited.assetId(), available - quantity);
                }

                if (send(limited)) {
                    openCount++; // ficou aberta no livro: conta para o limite nesta rodada
                }
            }
            lastError = null;
        } catch (RuntimeException e) {
            lastError = e.getClass().getSimpleName() + ": " + e.getMessage();
            count("agent.errors", "type", e.getClass().getSimpleName());
            log.warn("agent_tick_failed agent={} error={}", config.name(), lastError);
        }
    }

    private void cancelStale(List<Order> open) {
        OffsetDateTime limit = OffsetDateTime.now().minusSeconds(config.orderTtlSeconds());
        for (var iterator = open.iterator(); iterator.hasNext(); ) {
            Order order = iterator.next();
            if (order.createdAt() != null && order.createdAt().isBefore(limit)) {
                try {
                    client.cancel(order.id());
                    cancelled.incrementAndGet();
                    count("agent.orders.cancelled", "reason", "ttl");
                } catch (HttpStatusCodeException e) {
                    // Pode ter sido executada entre a leitura e o cancelamento; segue o fluxo.
                    log.debug("agent_cancel_conflict agent={} order={} status={}",
                            config.name(), order.id(), e.getStatusCode().value());
                }
                iterator.remove();
            }
        }
    }

    private boolean send(NewOrder order) {
        try {
            Order created = client.placeOrder(order);
            submitted.incrementAndGet();
            metrics.counter("agent.orders.submitted", "agent", config.name(), "side", order.side()).increment();
            log.info("agent_order agent={} side={} asset={} price={} qty={} status={}", config.name(),
                    order.side(), created.ticker(), order.price(), order.quantity(), created.status());
            return created.isOpen();
        } catch (HttpStatusCodeException e) {
            rejected.incrementAndGet();
            count("agent.orders.rejected", "status", String.valueOf(e.getStatusCode().value()));
            log.info("agent_order_rejected agent={} side={} status={} body={}", config.name(), order.side(),
                    e.getStatusCode().value(), e.getResponseBodyAsString());
            return false;
        }
    }

    private void count(String name, String tagKey, String tagValue) {
        metrics.counter(name, "agent", config.name(), tagKey, tagValue).increment();
    }

    public void pause() {
        active.set(false);
    }

    public void resume() {
        nextRunAt = Instant.EPOCH;
        active.set(true);
    }

    public AgentStatus status() {
        return new AgentStatus(config.name(), config.username(), config.strategy().name(), active.get(),
                config.intervalMs(), config.maxOrderQuantity(), config.maxOpenOrders(), config.orderTtlSeconds(),
                lastRunAt, lastError, submitted.get(), rejected.get(), cancelled.get());
    }

    public String name() {
        return config.name();
    }

    public record AgentStatus(String name, String username, String strategy, boolean active, long intervalMs,
                              long maxOrderQuantity, int maxOpenOrders, long orderTtlSeconds, Instant lastRunAt,
                              String lastError, long ordersSubmitted, long ordersRejected, long ordersCancelled) {
    }
}
