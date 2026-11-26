package br.ufs.stockmarket.ledger;

/**
 * Pedido incoerente com o estado das reservas (bug no chamador). Mapear para HTTP 500 e reverter.
 */
public class InvalidSettlementException extends LedgerException {
    public InvalidSettlementException(String message) {
        super(message);
    }
}
