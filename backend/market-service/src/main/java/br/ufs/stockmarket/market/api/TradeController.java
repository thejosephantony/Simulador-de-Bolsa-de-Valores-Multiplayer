package br.ufs.stockmarket.market.api;

import br.ufs.stockmarket.market.application.TradeService;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class TradeController {
    private final TradeService trades;
    public TradeController(TradeService trades) { this.trades = trades; }
    @GetMapping("/assets/{assetId}/trades")
    public List<PublicTradeResponse> forAsset(@PathVariable UUID assetId, @RequestParam(defaultValue = "50") int limit) {
        return trades.forAsset(assetId, limit);
    }
    @GetMapping("/trades/me")
    public List<MyTradeResponse> mine(JwtAuthenticationToken authentication, @RequestParam(defaultValue = "50") int limit) {
        return trades.mine(UUID.fromString(authentication.getToken().getSubject()), limit);
    }
}
