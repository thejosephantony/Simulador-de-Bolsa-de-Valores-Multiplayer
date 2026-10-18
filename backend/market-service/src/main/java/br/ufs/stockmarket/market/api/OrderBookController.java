package br.ufs.stockmarket.market.api;

import br.ufs.stockmarket.market.application.OrderBookService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/assets")
public class OrderBookController {

    private final OrderBookService orderBookService;

    public OrderBookController(OrderBookService orderBookService) {
        this.orderBookService = orderBookService;
    }

    @GetMapping("/{assetId}/order-book")
    public OrderBookResponse get(@PathVariable UUID assetId) {
        return orderBookService.get(assetId);
    }
}
