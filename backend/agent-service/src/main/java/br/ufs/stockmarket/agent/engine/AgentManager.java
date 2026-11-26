package br.ufs.stockmarket.agent.engine;

import br.ufs.stockmarket.agent.client.TradingClient;
import br.ufs.stockmarket.agent.config.AgentsProperties;
import br.ufs.stockmarket.agent.strategy.MarketMakerStrategy;
import br.ufs.stockmarket.agent.strategy.MomentumStrategy;
import br.ufs.stockmarket.agent.strategy.Strategy;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Cria um AgentRunner por agente configurado e dispara cada um no seu intervalo. */
@Component
public class AgentManager {

    private final Map<String, AgentRunner> runners = new LinkedHashMap<>();

    public AgentManager(AgentsProperties properties, RestClient.Builder restClient, MeterRegistry metrics) {
        for (AgentsProperties.AgentConfig config : properties.list()) {
            TradingClient client = new TradingClient(restClient, properties.authUrl(), properties.marketUrl(),
                    properties.portfolioUrl(), config.username(), config.password());
            Strategy strategy = switch (config.strategy()) {
                case MARKET_MAKER -> new MarketMakerStrategy();
                case MOMENTUM -> new MomentumStrategy();
            };
            runners.put(config.name(),
                    new AgentRunner(config, client, strategy, metrics, !properties.startPaused()));
        }
    }

    @Scheduled(fixedDelay = 250)
    public void tick() {
        Instant now = Instant.now();
        runners.values().stream().filter(runner -> runner.due(now)).forEach(AgentRunner::runOnce);
    }

    public List<AgentRunner.AgentStatus> statuses() {
        return runners.values().stream().map(AgentRunner::status).toList();
    }

    public Optional<AgentRunner> find(String name) {
        return Optional.ofNullable(runners.get(name));
    }
}
