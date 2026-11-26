package br.ufs.stockmarket.ledger;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Resultado da liberação de uma reserva.
 *
 * @param found            false quando a ordem não tem reserva (ordem legada da Parte 1).
 * @param released         true se esta chamada liberou recursos; false se já estava liberada/consumida.
 * @param releasedAmount   saldo devolvido (BUY); zero para SELL.
 * @param releasedQuantity quantidade devolvida (SELL) ou quantidade restante cancelada (BUY).
 */
public record ReleaseResult(
        UUID orderId,
        boolean found,
        boolean released,
        BigDecimal releasedAmount,
        long releasedQuantity
) {
    static ReleaseResult notFound(UUID orderId) {
        return new ReleaseResult(orderId, false, false, BigDecimal.ZERO, 0);
    }

    static ReleaseResult alreadyClosed(UUID orderId) {
        return new ReleaseResult(orderId, true, false, BigDecimal.ZERO, 0);
    }
}
