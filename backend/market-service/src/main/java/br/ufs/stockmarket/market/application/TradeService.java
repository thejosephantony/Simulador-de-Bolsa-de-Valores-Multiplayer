package br.ufs.stockmarket.market.application;

import br.ufs.stockmarket.market.api.MyTradeResponse;
import br.ufs.stockmarket.market.api.PublicTradeResponse;
import br.ufs.stockmarket.market.domain.AssetRepository;
import br.ufs.stockmarket.market.domain.TradeRepository;
import br.ufs.stockmarket.market.error.NotFoundException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
public class TradeService {
    private final TradeRepository trades;
    private final AssetRepository assets;
    public TradeService(TradeRepository trades, AssetRepository assets) { this.trades = trades; this.assets = assets; }
    @Transactional(readOnly = true)
    public List<PublicTradeResponse> forAsset(UUID assetId, int limit) {
        if (!assets.existsById(assetId)) throw new NotFoundException("Ativo não encontrado.");
        return trades.findAllByAssetIdOrderByExecutedAtDescIdDesc(assetId, page(limit)).stream()
                .map(PublicTradeResponse::from).toList();
    }
    @Transactional(readOnly = true)
    public List<MyTradeResponse> mine(UUID userId, int limit) {
        return trades.findAllByBuyerIdOrSellerIdOrderByExecutedAtDescIdDesc(userId, userId, page(limit)).stream()
                .map(t -> MyTradeResponse.from(t, userId)).toList();
    }
    private PageRequest page(int limit) { return PageRequest.of(0, Math.max(1, Math.min(100, limit))); }
}
