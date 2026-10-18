package br.ufs.stockmarket.market.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<OrderEntity, UUID> {

    List<OrderEntity> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<OrderEntity> findByIdAndUserId(UUID id, UUID userId);

    List<OrderEntity> findAllByAssetIdAndStatus(UUID assetId, OrderStatus status);
}
