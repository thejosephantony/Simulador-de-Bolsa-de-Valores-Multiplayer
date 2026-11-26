package br.ufs.stockmarket.market.error;

public class OrderTemporarilyUnavailableException extends RuntimeException {
    public OrderTemporarilyUnavailableException(Throwable cause) {
        super("Conflito transacional temporário. Tente novamente.", cause);
    }
}
