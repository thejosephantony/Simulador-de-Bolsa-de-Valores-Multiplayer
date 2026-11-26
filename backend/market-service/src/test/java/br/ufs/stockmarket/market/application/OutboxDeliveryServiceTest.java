package br.ufs.stockmarket.market.application;

import br.ufs.stockmarket.market.domain.OutboxDeliveryStore;
import br.ufs.stockmarket.market.domain.OutboxDeliveryStore.PendingEvent;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OutboxDeliveryServiceTest {
    private final OutboxDeliveryStore store = mock(OutboxDeliveryStore.class);
    private final RabbitEventTransport transport = mock(RabbitEventTransport.class);
    private final OutboxDeliveryService service = new OutboxDeliveryService(store, transport);
    private final PendingEvent event = new PendingEvent(UUID.randomUUID(), "trade.executed", 0, "{}");

    @Test void emptyDoesNotPublish() {
        when(store.lockNext()).thenReturn(Optional.empty());
        assertEquals(OutboxDeliveryService.Result.EMPTY, service.publishOne());
        verifyNoInteractions(transport);
    }
    @Test void ackMarksPublished() throws Exception {
        when(store.lockNext()).thenReturn(Optional.of(event));
        assertEquals(OutboxDeliveryService.Result.PUBLISHED, service.publishOne());
        var ordered = inOrder(transport, store);
        ordered.verify(transport).publish(event);
        ordered.verify(store).markPublished(event.eventId());
    }
    @Test void failureRemainsPending() throws Exception {
        when(store.lockNext()).thenReturn(Optional.of(event));
        doThrow(new IllegalStateException("failure")).when(transport).publish(event);
        assertEquals(OutboxDeliveryService.Result.RETRY, service.publishOne());
        verify(store).markFailed(event.eventId(), 1L, "IllegalStateException");
        verify(store, never()).markPublished(any());
    }
    @Test void retryDelayHasLimit() {
        assertEquals(1, OutboxDeliveryService.retryDelay(0));
        assertEquals(8, OutboxDeliveryService.retryDelay(3));
        assertEquals(60, OutboxDeliveryService.retryDelay(1000));
    }
    @Test void routingSeparatesPrivateAndPublic() {
        assertEquals("market.public.trade.executed", event.routingKey());
        assertEquals("market.private.order.changed", new PendingEvent(event.eventId(), "order.changed", 0, "{}").routingKey());
        assertThrows(IllegalArgumentException.class, () -> new PendingEvent(event.eventId(), "unknown", 0, "{}").routingKey());
    }
}
