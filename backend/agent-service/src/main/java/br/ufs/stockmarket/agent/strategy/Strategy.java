package br.ufs.stockmarket.agent.strategy;

import br.ufs.stockmarket.agent.client.ApiModels.Asset;
import br.ufs.stockmarket.agent.client.ApiModels.NewOrder;
import br.ufs.stockmarket.agent.client.ApiModels.Order;
import br.ufs.stockmarket.agent.config.AgentsProperties.AgentConfig;

import java.util.List;

/** Regra de decisão de um agente. Só decide; quem envia e aplica os limites é o AgentRunner. */
public interface Strategy {

    List<NewOrder> decide(AgentConfig config, List<Asset> assets, List<Order> openOrders);
}
