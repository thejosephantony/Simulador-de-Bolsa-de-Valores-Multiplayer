package br.ufs.stockmarket.market.application;

import br.ufs.stockmarket.market.domain.OutboxDeliveryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxDeliveryService {
    public enum Result { EMPTY, PUBLISHED, RETRY }
    private static final Logger log = LoggerFactory.getLogger(OutboxDeliveryService.class);
    private final OutboxDeliveryStore store;
    private final RabbitEventTransport transport;

    public OutboxDeliveryService(OutboxDeliveryStore store, RabbitEventTransport transport) {
        this.store = store;
        this.transport = transport;
    }

    @Transactional(timeout = 20)
    public Result publishOne() {
        var pending = store.lockNext();
        if (pending.isEmpty()) return Result.EMPTY;
        var event = pending.get();
        try {
            transport.publish(event);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Outbox delivery interrupted", e);
        } catch (Exception e) {
            // Keep the event, do not log its private JSON or credentials.
            long delay = retryDelay(event.attempts());
            store.markFailed(event.eventId(), delay, e.getClass().getSimpleName());
            log.warn("outbox_retry eventId={} type={} attempts={} delaySeconds={} error={}",
                    event.eventId(), event.type(), (long) event.attempts() + 1, delay, e.getClass().getSimpleName());
            return Result.RETRY;
        }
        // If DB commit fails after ACK, the event is retried; consumers must deduplicate.
        store.markPublished(event.eventId());
        log.debug("outbox_ack eventId={} type={}", event.eventId(), event.type());
        return Result.PUBLISHED;
    }

    static long retryDelay(int priorAttempts) {
        return Math.min(60L, 1L << Math.min(6, Math.max(0, priorAttempts)));
    }
}
