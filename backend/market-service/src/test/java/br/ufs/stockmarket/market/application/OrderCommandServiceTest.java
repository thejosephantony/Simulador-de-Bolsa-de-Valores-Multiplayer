package br.ufs.stockmarket.market.application;

import br.ufs.stockmarket.market.api.CreateOrderRequest;
import br.ufs.stockmarket.market.api.OrderResponse;
import br.ufs.stockmarket.market.error.InvalidOrderException;
import br.ufs.stockmarket.market.error.OrderTemporarilyUnavailableException;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderCommandServiceTest {
    private final OrderService orders = mock(OrderService.class);
    private final OrderCommandService commands = new OrderCommandService(orders);
    private final UUID user = UUID.randomUUID();
    private final UUID order = UUID.randomUUID();
    private final CreateOrderRequest request = mock(CreateOrderRequest.class);
    private final OrderResponse response = mock(OrderResponse.class);
    private static RuntimeException failure(String state) {
        return new RuntimeException(new SQLException("test conflict", state));
    }

    @Test void retriesDeadlockAndKeepsSameKey() {
        when(orders.create(user, request, "request-1"))
                .thenThrow(failure("40P01")).thenReturn(response);
        assertSame(response, commands.create(user, request, "request-1"));
        verify(orders, times(2)).create(user, request, "request-1");
    }

    @Test void retriesSerializationFailureDuringCancellation() {
        when(orders.cancel(user, order)).thenThrow(failure("40001")).thenReturn(response);
        assertSame(response, commands.cancel(user, order));
        verify(orders, times(2)).cancel(user, order);
    }

    @Test void stopsAfterThreeAttempts() {
        when(orders.create(user, request, "request-1")).thenThrow(failure("40P01"));
        assertThrows(OrderTemporarilyUnavailableException.class,
                () -> commands.create(user, request, "request-1"));
        verify(orders, times(3)).create(user, request, "request-1");
    }

    @Test void doesNotRetryBusinessError() {
        var error = new InvalidOrderException("rejected");
        when(orders.create(user, request, "request-1")).thenThrow(error);
        assertSame(error, assertThrows(InvalidOrderException.class,
                () -> commands.create(user, request, "request-1")));
        verify(orders).create(user, request, "request-1");
    }

    @Test void doesNotRetryAmbiguousConnectionFailure() {
        var error = failure("08006");
        when(orders.cancel(user, order)).thenThrow(error);
        assertSame(error, assertThrows(RuntimeException.class, () -> commands.cancel(user, order)));
        verify(orders).cancel(user, order);
    }
}
