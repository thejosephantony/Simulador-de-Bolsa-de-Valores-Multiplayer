package br.ufs.stockmarket.market.application;

import br.ufs.stockmarket.market.config.MarketMessagingConfig;
import br.ufs.stockmarket.market.domain.OutboxDeliveryStore.PendingEvent;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class RabbitEventTransport {
    private final RabbitTemplate rabbit;

    public RabbitEventTransport(RabbitTemplate rabbit) {
        this.rabbit = rabbit;
    }

    public void publish(PendingEvent event) throws Exception {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setContentEncoding("UTF-8");
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        properties.setMessageId(event.eventId().toString());
        properties.setType(event.type());
        Message message = new Message(event.envelope().getBytes(StandardCharsets.UTF_8), properties);
        // A new correlation ID per attempt; the stable message ID supports consumer deduplication.
        CorrelationData correlation = new CorrelationData(UUID.randomUUID().toString());
        rabbit.send(MarketMessagingConfig.EXCHANGE, event.routingKey(), message, correlation);
        CorrelationData.Confirm confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
        if (!confirm.ack()) {
            throw new IllegalStateException("Broker NACK");
        }
        if (correlation.getReturned() != null) {
            throw new IllegalStateException("Unroutable event");
        }
    }
}
