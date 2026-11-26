package br.ufs.stockmarket.ledger;

/** Quantidade disponível insuficiente para reservar a venda (sem venda a descoberto). Mapear para HTTP 422. */
public class InsufficientPositionException extends LedgerException {
    public InsufficientPositionException(String message) {
        super(message);
    }
}
