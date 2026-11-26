package br.ufs.stockmarket.market.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "market.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisher {
    private final OutboxDeliveryService delivery;

    public OutboxPublisher(OutboxDeliveryService delivery) {
        this.delivery = delivery;
    }

    @Scheduled(fixedDelayString = "${market.outbox.delay-ms:500}")
    public void publishBatch() {
        for (int i = 0; i < 20; i++) {
            if (delivery.publishOne() != OutboxDeliveryService.Result.PUBLISHED) return;
        }
    }
}
