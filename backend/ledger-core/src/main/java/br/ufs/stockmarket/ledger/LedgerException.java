package br.ufs.stockmarket.ledger;

/**
 * Violação de regra financeira. A transação do chamador deve ser revertida.
 */
public class LedgerException extends RuntimeException {
    public LedgerException(String message) {
        super(message);
    }
}
