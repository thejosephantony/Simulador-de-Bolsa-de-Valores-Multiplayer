package br.ufs.stockmarket.market.api;

import br.ufs.stockmarket.market.application.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse create(
            JwtAuthenticationToken authentication,
            @Valid @RequestBody CreateOrderRequest request
    ) {
        return orderService.create(userId(authentication), request);
    }

    @GetMapping
    public List<OrderResponse> listMine(JwtAuthenticationToken authentication) {
        return orderService.listMine(userId(authentication));
    }

    @GetMapping("/{orderId}")
    public OrderResponse findMine(
            JwtAuthenticationToken authentication,
            @PathVariable UUID orderId
    ) {
        return orderService.findMine(userId(authentication), orderId);
    }

    @DeleteMapping("/{orderId}")
    public OrderResponse cancel(
            JwtAuthenticationToken authentication,
            @PathVariable UUID orderId
    ) {
        return orderService.cancel(userId(authentication), orderId);
    }

    private UUID userId(JwtAuthenticationToken authentication) {
        return UUID.fromString(authentication.getToken().getSubject());
    }
}
