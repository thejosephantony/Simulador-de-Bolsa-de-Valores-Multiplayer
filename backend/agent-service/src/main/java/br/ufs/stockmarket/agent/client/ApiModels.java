package br.ufs.stockmarket.agent.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Subconjunto das respostas REST do Auth, Market e Portfolio usado pelos agentes. */
public final class ApiModels {

    private ApiModels() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LoginResponse(String accessToken, long expiresInSeconds) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Asset(UUID id, String ticker, BigDecimal currentPrice, BigDecimal bestBid, BigDecimal bestAsk) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Order(UUID id, UUID assetId, String ticker, String side, BigDecimal price, long quantity,
                        String status, OffsetDateTime createdAt, long remainingQuantity) {
        public boolean isOpen() {
            return "OPEN".equals(status) || "PARTIALLY_FILLED".equals(status);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Position(UUID assetId, long availableQuantity) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Portfolio(BigDecimal availableBalance, List<Position> positions) {
        public long available(UUID assetId) {
            return positions == null ? 0 : positions.stream()
                    .filter(p -> p.assetId().equals(assetId))
                    .mapToLong(Position::availableQuantity)
                    .findFirst().orElse(0);
        }
    }

    public record NewOrder(UUID assetId, String side, BigDecimal price, long quantity) {
    }
}
