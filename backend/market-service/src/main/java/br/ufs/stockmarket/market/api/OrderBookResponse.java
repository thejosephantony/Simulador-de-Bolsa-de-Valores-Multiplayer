package br.ufs.stockmarket.market.api;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record OrderBookResponse(
        UUID assetId,
        String ticker,
        List<BookLevelResponse> buys,
        List<BookLevelResponse> sells,
        OffsetDateTime generatedAt
) {
}
