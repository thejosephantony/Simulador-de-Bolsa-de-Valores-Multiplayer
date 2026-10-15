package br.ufs.stockmarket.market.application;

import br.ufs.stockmarket.market.api.AssetResponse;
import br.ufs.stockmarket.market.domain.AssetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AssetService {

    private final AssetRepository assetRepository;

    public AssetService(AssetRepository assetRepository) {
        this.assetRepository = assetRepository;
    }

    @Transactional(readOnly = true)
    public List<AssetResponse> listActiveAssets() {
        return assetRepository.findAllByActiveTrueOrderByTickerAsc()
                .stream()
                .map(asset -> new AssetResponse(
                        asset.getId(),
                        asset.getTicker(),
                        asset.getName(),
                        asset.getInitialPrice(),
                        asset.getCurrentPrice()
                ))
                .toList();
    }
}
