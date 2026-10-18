package br.ufs.stockmarket.market.application;

import br.ufs.stockmarket.market.api.CreateOrderRequest;
import br.ufs.stockmarket.market.api.OrderResponse;
import br.ufs.stockmarket.market.domain.AssetEntity;
import br.ufs.stockmarket.market.domain.AssetRepository;
import br.ufs.stockmarket.market.domain.OrderEntity;
import br.ufs.stockmarket.market.domain.OrderRepository;
import br.ufs.stockmarket.market.domain.OrderStatus;
import br.ufs.stockmarket.market.error.InvalidOrderException;
import br.ufs.stockmarket.market.error.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final AssetRepository assetRepository;

    public OrderService(OrderRepository orderRepository, AssetRepository assetRepository) {
        this.orderRepository = orderRepository;
        this.assetRepository = assetRepository;
    }

    @Transactional
    public OrderResponse create(UUID userId, CreateOrderRequest request) {
        AssetEntity asset = findActiveAsset(request.assetId());

        OrderEntity order = orderRepository.save(new OrderEntity(
                userId,
                asset.getId(),
                request.side(),
                request.price(),
                request.quantity()
        ));

        return OrderResponse.from(order, asset.getTicker());
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> listMine(UUID userId) {
        return orderRepository.findAllByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(order -> OrderResponse.from(order, tickerFor(order.getAssetId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse findMine(UUID userId, UUID orderId) {
        OrderEntity order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new NotFoundException("Ordem não encontrada."));
        return OrderResponse.from(order, tickerFor(order.getAssetId()));
    }

    @Transactional
    public OrderResponse cancel(UUID userId, UUID orderId) {
        OrderEntity order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new NotFoundException("Ordem não encontrada."));

        if (order.getStatus() != OrderStatus.OPEN) {
            throw new InvalidOrderException("A ordem não está aberta para cancelamento.");
        }

        order.cancel();
        return OrderResponse.from(order, tickerFor(order.getAssetId()));
    }

    private AssetEntity findActiveAsset(UUID assetId) {
        AssetEntity asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new NotFoundException("Ativo não encontrado."));
        if (!asset.isActive()) {
            throw new InvalidOrderException("Ativo não está disponível para negociação.");
        }
        return asset;
    }

    private String tickerFor(UUID assetId) {
        return assetRepository.findById(assetId)
                .map(AssetEntity::getTicker)
                .orElse("UNKNOWN");
    }
}
