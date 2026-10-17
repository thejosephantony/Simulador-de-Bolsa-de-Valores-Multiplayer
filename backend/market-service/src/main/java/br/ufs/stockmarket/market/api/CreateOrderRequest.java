package br.ufs.stockmarket.market.api;

import br.ufs.stockmarket.market.domain.OrderSide;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateOrderRequest(
        @NotNull(message = "userId é obrigatório") UUID userId,
        @NotNull(message = "assetId é obrigatório") UUID assetId,
        @NotNull(message = "side é obrigatório e deve ser BUY ou SELL") OrderSide side,
        @NotNull(message = "price é obrigatório")
        @DecimalMin(value = "0", inclusive = false, message = "price deve ser maior que zero")
        @Digits(integer = 15, fraction = 4, message = "price deve ter até 15 dígitos inteiros e 4 decimais")
        BigDecimal price,
        @NotNull(message = "quantity é obrigatória")
        @Positive(message = "quantity deve ser maior que zero") Long quantity
) {
}
