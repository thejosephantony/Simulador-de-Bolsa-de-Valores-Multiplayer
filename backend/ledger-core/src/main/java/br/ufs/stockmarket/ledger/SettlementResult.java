package br.ufs.stockmarket.ledger;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Resultado da liquidação.
 *
 * @param duplicate          true se o trade já havia sido liquidado; nada foi alterado.
 * @param grossAmount        preço × quantidade debitado do comprador e creditado ao vendedor.
 * @param buyerReleasedExcess (limite − preço) × quantidade devolvido ao saldo disponível do comprador.
 * @param buyOrderFilled     a reserva da ordem de compra foi totalmente consumida.
 * @param sellOrderFilled    a reserva da ordem de venda foi totalmente consumida.
 */
public record SettlementResult(
        UUID tradeId,
        boolean duplicate,
        BigDecimal grossAmount,
        BigDecimal buyerReleasedExcess,
        boolean buyOrderFilled,
        boolean sellOrderFilled
) {
}
