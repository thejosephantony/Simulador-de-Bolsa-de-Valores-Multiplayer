package br.ufs.stockmarket.market.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<OrderEntity, UUID> {

    List<OrderEntity> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<OrderEntity> findByIdAndUserId(UUID id, UUID userId);

    @Query("select o.assetId from OrderEntity o where o.id = :orderId and o.userId = :userId")
    Optional<UUID> findAssetIdForOwner(@Param("orderId") UUID orderId, @Param("userId") UUID userId);

    List<OrderEntity> findAllByAssetIdAndStatus(UUID assetId, OrderStatus status);

    List<OrderEntity> findAllByAssetIdAndStatusIn(UUID assetId, Collection<OrderStatus> statuses);

    // Chamadas de matching devem ocorrer sob o bloqueio do ativo.
    List<OrderEntity> findAllByAssetIdAndSideAndStatusInAndPriceLessThanEqualOrderByPriceAscSequenceNumberAsc(
            UUID assetId, OrderSide side, Collection<OrderStatus> statuses, BigDecimal limitPrice);

    List<OrderEntity> findAllByAssetIdAndSideAndStatusInAndPriceGreaterThanEqualOrderByPriceDescSequenceNumberAsc(
            UUID assetId, OrderSide side, Collection<OrderStatus> statuses, BigDecimal limitPrice);
}
