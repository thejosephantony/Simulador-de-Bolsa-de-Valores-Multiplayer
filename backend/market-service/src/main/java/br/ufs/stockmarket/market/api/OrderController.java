package br.ufs.stockmarket.market.api;

import br.ufs.stockmarket.market.application.OrderService;
import br.ufs.stockmarket.market.application.OrderCommandService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {
    private final OrderService orderService;
    private final OrderCommandService commands;
    public OrderController(OrderService orderService, OrderCommandService commands) {
        this.orderService = orderService;
        this.commands = commands;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse create(JwtAuthenticationToken authentication,
            @Valid @RequestBody CreateOrderRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String requestKey) {
        return commands.create(userId(authentication), request, requestKey);
    }
    @GetMapping
    public List<OrderResponse> listMine(JwtAuthenticationToken authentication) {
        return orderService.listMine(userId(authentication));
    }
    @GetMapping("/{orderId}")
    public OrderResponse findMine(JwtAuthenticationToken authentication, @PathVariable UUID orderId) {
        return orderService.findMine(userId(authentication), orderId);
    }
    @DeleteMapping("/{orderId}")
    public OrderResponse cancel(JwtAuthenticationToken authentication, @PathVariable UUID orderId) {
        return commands.cancel(userId(authentication), orderId);
    }
    private UUID userId(JwtAuthenticationToken authentication) {
        return UUID.fromString(authentication.getToken().getSubject());
    }
}
