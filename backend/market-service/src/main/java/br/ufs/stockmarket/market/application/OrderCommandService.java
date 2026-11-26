package br.ufs.stockmarket.market.application;

import br.ufs.stockmarket.market.api.CreateOrderRequest;
import br.ufs.stockmarket.market.api.OrderResponse;
import br.ufs.stockmarket.market.error.OrderTemporarilyUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.SQLException;
import java.util.UUID;
import java.util.function.Supplier;

/** Entry point outside the transaction; every call to the proxied OrderService starts a new transaction. */
@Service
public class OrderCommandService {
    private static final Logger log = LoggerFactory.getLogger(OrderCommandService.class);
    private static final int MAX_ATTEMPTS = 3;
    private final OrderService orders;

    public OrderCommandService(OrderService orders) {
        this.orders = orders;
    }

    public OrderResponse create(UUID userId, CreateOrderRequest request, String key) {
        return execute("create", () -> orders.create(userId, request, key));
    }

    public OrderResponse cancel(UUID userId, UUID orderId) {
        return execute("cancel", () -> orders.cancel(userId, orderId));
    }

    private OrderResponse execute(String operation, Supplier<OrderResponse> action) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("OrderCommandService requires an outer non-transactional caller.");
        }
        for (int attempt = 1; ; attempt++) {
            try {
                return action.get();
            } catch (RuntimeException ex) {
                if (!retryable(ex)) throw ex;
                log.warn("order_transaction_conflict operation={} attempt={}", operation, attempt);
                if (attempt == MAX_ATTEMPTS) {
                    throw new OrderTemporarilyUnavailableException(ex);
                }
                try {
                    Thread.sleep(25L * (1L << (attempt - 1)));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new OrderTemporarilyUnavailableException(interrupted);
                }
            }
        }
    }

    // Only SQLSTATEs that indicate an aborted transaction, never an ambiguous network/commit failure.
    static boolean retryable(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql) {
                if ("40P01".equals(sql.getSQLState()) || "40001".equals(sql.getSQLState())) return true;
            }
            if (cause == cause.getCause()) break;
        }
        return false;
    }
}
