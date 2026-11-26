package br.ufs.stockmarket.agent.strategy;

import java.math.BigDecimal;
import java.math.RoundingMode;

final class Prices {

    static final BigDecimal TICK = new BigDecimal("0.01");

    private Prices() {
    }

    /** Preço com 2 casas, nunca abaixo de 0,01. */
    static BigDecimal tick(BigDecimal value) {
        BigDecimal rounded = value.setScale(2, RoundingMode.HALF_UP);
        return rounded.compareTo(TICK) < 0 ? TICK : rounded;
    }
}
