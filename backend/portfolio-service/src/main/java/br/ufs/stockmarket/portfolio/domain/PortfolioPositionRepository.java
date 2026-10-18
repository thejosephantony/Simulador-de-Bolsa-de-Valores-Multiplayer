package br.ufs.stockmarket.portfolio.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PortfolioPositionRepository extends JpaRepository<PortfolioPositionEntity, UUID> {

    List<PortfolioPositionEntity> findAllByUserIdOrderByAssetIdAsc(UUID userId);
}
