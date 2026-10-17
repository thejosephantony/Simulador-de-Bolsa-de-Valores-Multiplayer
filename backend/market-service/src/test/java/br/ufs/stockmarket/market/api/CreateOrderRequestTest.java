package br.ufs.stockmarket.market.api;

import br.ufs.stockmarket.market.domain.OrderSide;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CreateOrderRequestTest {

    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();

    @AfterAll
    static void closeValidatorFactory() {
        FACTORY.close();
    }

    @Test
    void requiresAllFiveClientFields() {
        assertThat(VALIDATOR.validate(new CreateOrderRequest(null, null, null, null, null)))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactlyInAnyOrder("userId", "assetId", "side", "price", "quantity");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-0.0001", "0.00001", "1000000000000000", "25.12345"})
    void rejectsNonPositiveOrUnrepresentablePrices(String price) {
        assertThat(VALIDATOR.validate(request(price, 1L)))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("price");
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, Long.MIN_VALUE})
    void rejectsNonPositiveQuantities(long quantity) {
        assertThat(VALIDATOR.validate(request("25.0000", quantity)))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("quantity");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.0001", "25", "25.1234", "999999999999999.9999"})
    void acceptsPricesAtSupportedBoundaries(String price) {
        assertThat(VALIDATOR.validate(request(price, Long.MAX_VALUE))).isEmpty();
    }

    private CreateOrderRequest request(String price, long quantity) {
        return new CreateOrderRequest(
                UUID.randomUUID(), UUID.randomUUID(), OrderSide.BUY, new BigDecimal(price), quantity
        );
    }
}
