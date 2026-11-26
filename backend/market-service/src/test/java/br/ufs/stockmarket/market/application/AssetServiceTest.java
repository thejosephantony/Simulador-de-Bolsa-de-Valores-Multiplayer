package br.ufs.stockmarket.market.application;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;

class AssetServiceTest {
    @Test void unchangedPrice() { check("25", "25", "0.0000"); }
    @Test void priceIncrease() { check("20", "25", "25.0000"); }
    @Test void priceDecrease() { check("25", "20", "-20.0000"); }
    @Test void fractionalChange() { check("3", "4", "33.3333"); }
    private void check(String initial, String current, String expected) {
        assertThat(AssetService.priceChangePercent(new BigDecimal(initial), new BigDecimal(current)))
                .isEqualByComparingTo(new BigDecimal(expected));
    }
}
