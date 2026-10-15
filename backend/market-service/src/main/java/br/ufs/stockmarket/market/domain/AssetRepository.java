package br.ufs.stockmarket.market.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AssetRepository extends JpaRepository<AssetEntity, UUID> {

    List<AssetEntity> findAllByActiveTrueOrderByTickerAsc();
}
