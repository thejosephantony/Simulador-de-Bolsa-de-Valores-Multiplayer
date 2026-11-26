package br.ufs.stockmarket.market.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssetRepository extends JpaRepository<AssetEntity, UUID> {

    List<AssetEntity> findAllByActiveTrueOrderByTickerAsc();

    // Executar dentro da transação que aceita, executa ou cancela ordens.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AssetEntity a where a.id = :id")
    Optional<AssetEntity> findByIdForUpdate(@Param("id") UUID id);
}
