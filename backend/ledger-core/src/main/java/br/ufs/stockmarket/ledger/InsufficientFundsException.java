package br.ufs.stockmarket.ledger;

/** Saldo disponível insuficiente para reservar a compra. Mapear para HTTP 422. */
public class InsufficientFundsException extends LedgerException {
    public InsufficientFundsException(String message) {
        super(message);
    }
}
