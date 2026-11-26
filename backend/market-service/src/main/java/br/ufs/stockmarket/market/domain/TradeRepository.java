package br.ufs.stockmarket.market.domain;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TradeRepository extends JpaRepository<TradeEntity, UUID> {

    List<TradeEntity> findAllByAssetIdOrderByExecutedAtDescIdDesc(UUID assetId, Pageable pageable);

    List<TradeEntity> findAllByBuyerIdOrSellerIdOrderByExecutedAtDescIdDesc(
            UUID buyerId, UUID sellerId, Pageable pageable);
}
