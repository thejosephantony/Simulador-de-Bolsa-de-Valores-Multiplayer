package br.ufs.stockmarket.market.application;

import br.ufs.stockmarket.market.api.CreateOrderRequest;
import br.ufs.stockmarket.market.api.OrderResponse;
import br.ufs.stockmarket.market.domain.AssetEntity;
import br.ufs.stockmarket.market.domain.AssetRepository;
import br.ufs.stockmarket.market.domain.OrderEntity;
import br.ufs.stockmarket.market.domain.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private final AssetRepository assetRepository;
    private final OrderRepository orderRepository;

    public OrderService(AssetRepository assetRepository, OrderRepository orderRepository) {
        this.assetRepository = assetRepository;
        this.orderRepository = orderRepository;
    }

    @Transactional
    public OrderResponse create(CreateOrderRequest request) {
        AssetEntity asset = assetRepository.findById(request.assetId())
                .orElseThrow(() -> new InvalidOrderException("O ativo informado não existe"));

        if (!asset.isActive()) {
            throw new InvalidOrderException("O ativo informado está inativo");
        }

        OrderEntity order = new OrderEntity(
                request.userId(), asset.getId(), request.side(), request.price(), request.quantity()
        );
        // Flush retrieves the database-generated sequence before building the response.
        OrderEntity saved = orderRepository.saveAndFlush(order);

        return new OrderResponse(
                saved.getId(),
                saved.getUserId(),
                saved.getAssetId(),
                saved.getSide(),
                saved.getPrice(),
                saved.getQuantity(),
                saved.getRemainingQuantity(),
                saved.getStatus(),
                saved.getSequenceNumber(),
                saved.getCreatedAt()
        );
    }
}
