package br.ufs.stockmarket.market.application;

import br.ufs.stockmarket.market.api.CreateOrderRequest;
import br.ufs.stockmarket.market.api.OrderResponse;
import br.ufs.stockmarket.market.domain.AssetEntity;
import br.ufs.stockmarket.market.domain.AssetRepository;
import br.ufs.stockmarket.market.domain.OrderEntity;
import br.ufs.stockmarket.market.domain.OrderRepository;
import br.ufs.stockmarket.market.domain.OrderSide;
import br.ufs.stockmarket.market.domain.OrderStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID ASSET_ID = UUID.randomUUID();

    @Mock
    private AssetRepository assetRepository;
    @Mock
    private OrderRepository orderRepository;
    @InjectMocks
    private OrderService orderService;

    @ParameterizedTest
    @EnumSource(OrderSide.class)
    void createsPendingOrderWithServerManagedFields(OrderSide side) {
        AssetEntity asset = mock(AssetEntity.class);
        when(assetRepository.findById(ASSET_ID)).thenReturn(Optional.of(asset));
        when(asset.isActive()).thenReturn(true);
        when(asset.getId()).thenReturn(ASSET_ID);
        UUID generatedId = UUID.randomUUID();
        when(orderRepository.saveAndFlush(any(OrderEntity.class))).thenAnswer(invocation -> {
            OrderEntity order = invocation.getArgument(0);
            assertThat(order.getId()).isNull();
            assertThat(order.getSequenceNumber()).isNull();
            assertThat(order.getVersion()).isZero();
            ReflectionTestUtils.setField(order, "id", generatedId);
            ReflectionTestUtils.setField(order, "sequenceNumber", 42L);
            return order;
        });

        OrderResponse response = orderService.create(request(side));

        assertThat(response.id()).isEqualTo(generatedId);
        assertThat(response.userId()).isEqualTo(USER_ID);
        assertThat(response.assetId()).isEqualTo(ASSET_ID);
        assertThat(response.side()).isEqualTo(side);
        assertThat(response.price()).isEqualByComparingTo("25.1234");
        assertThat(response.quantity()).isEqualTo(100);
        assertThat(response.remainingQuantity()).isEqualTo(100);
        assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.sequenceNumber()).isEqualTo(42);
        assertThat(response.createdAt()).isNotNull();
        verify(orderRepository).saveAndFlush(any(OrderEntity.class));
    }

    @Test
    void rejectsUnknownAssetWithoutPersistingOrder() {
        when(assetRepository.findById(ASSET_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.create(request(OrderSide.BUY)))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessage("O ativo informado não existe");

        verifyNoInteractions(orderRepository);
    }

    @Test
    void rejectsInactiveAssetWithoutPersistingOrder() {
        AssetEntity asset = mock(AssetEntity.class);
        when(assetRepository.findById(ASSET_ID)).thenReturn(Optional.of(asset));
        when(asset.isActive()).thenReturn(false);

        assertThatThrownBy(() -> orderService.create(request(OrderSide.SELL)))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessage("O ativo informado está inativo");

        verifyNoInteractions(orderRepository);
    }

    private CreateOrderRequest request(OrderSide side) {
        return new CreateOrderRequest(USER_ID, ASSET_ID, side, new BigDecimal("25.1234"), 100L);
    }
}
