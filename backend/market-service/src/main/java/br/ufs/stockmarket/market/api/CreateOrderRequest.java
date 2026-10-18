package br.ufs.stockmarket.market.api;

import br.ufs.stockmarket.market.domain.OrderSide;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateOrderRequest(
        @NotNull UUID assetId,
        @NotNull OrderSide side,
        @NotNull @DecimalMin(value = "0.0001") BigDecimal price,
        @Positive long quantity
) {
}
