package br.ufs.stockmarket.market.application;

import br.ufs.stockmarket.market.api.BookLevelResponse;
import br.ufs.stockmarket.market.api.OrderBookResponse;
import br.ufs.stockmarket.market.domain.AssetEntity;
import br.ufs.stockmarket.market.domain.AssetRepository;
import br.ufs.stockmarket.market.domain.OrderEntity;
import br.ufs.stockmarket.market.domain.OrderRepository;
import br.ufs.stockmarket.market.domain.OrderSide;
import br.ufs.stockmarket.market.domain.OrderStatus;
import br.ufs.stockmarket.market.error.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

@Service
public class OrderBookService {

    private final AssetRepository assetRepository;
    private final OrderRepository orderRepository;

    public OrderBookService(AssetRepository assetRepository, OrderRepository orderRepository) {
        this.assetRepository = assetRepository;
        this.orderRepository = orderRepository;
    }

    @Transactional(readOnly = true)
    public OrderBookResponse get(UUID assetId) {
        AssetEntity asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new NotFoundException("Ativo não encontrado."));

        List<OrderEntity> openOrders = orderRepository.findAllByAssetIdAndStatus(assetId, OrderStatus.OPEN);

        return new OrderBookResponse(
                asset.getId(),
                asset.getTicker(),
                aggregate(openOrders, OrderSide.BUY, Comparator.reverseOrder()),
                aggregate(openOrders, OrderSide.SELL, Comparator.naturalOrder()),
                OffsetDateTime.now()
        );
    }

    private List<BookLevelResponse> aggregate(
            List<OrderEntity> orders,
            OrderSide side,
            Comparator<BigDecimal> priceComparator
    ) {
        Map<BigDecimal, MutableLevel> levels = new TreeMap<>(priceComparator);

        orders.stream()
                .filter(order -> order.getSide() == side)
                .forEach(order -> levels
                        .computeIfAbsent(order.getPrice(), ignored -> new MutableLevel())
                        .add(order.getQuantity()));

        List<BookLevelResponse> result = new ArrayList<>();
        levels.forEach((price, level) ->
                result.add(new BookLevelResponse(price, level.quantity, level.orderCount))
        );
        return result;
    }

    private static final class MutableLevel {
        private long quantity;
        private long orderCount;

        private void add(long orderQuantity) {
            quantity += orderQuantity;
            orderCount++;
        }
    }
}
