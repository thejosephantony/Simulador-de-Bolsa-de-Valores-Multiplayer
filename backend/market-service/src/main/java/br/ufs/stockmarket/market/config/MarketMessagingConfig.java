package br.ufs.stockmarket.market.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class MarketMessagingConfig {
    public static final String EXCHANGE = "market.events";
    public static final String REALTIME_QUEUE = "market.realtime.events";

    @Bean
    TopicExchange marketEventsExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    Queue marketRealtimeQueue() {
        return QueueBuilder.durable(REALTIME_QUEUE).build();
    }

    @Bean
    Binding marketPublicBinding(Queue marketRealtimeQueue, TopicExchange marketEventsExchange) {
        return BindingBuilder.bind(marketRealtimeQueue).to(marketEventsExchange).with("market.public.#");
    }

    @Bean
    Binding marketPrivateBinding(Queue marketRealtimeQueue, TopicExchange marketEventsExchange) {
        return BindingBuilder.bind(marketRealtimeQueue).to(marketEventsExchange).with("market.private.#");
    }
}
