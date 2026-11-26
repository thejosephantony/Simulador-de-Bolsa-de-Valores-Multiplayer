package br.ufs.stockmarket.market.application;

import br.ufs.stockmarket.ledger.LedgerService;
import br.ufs.stockmarket.ledger.TradeSettlement;
import br.ufs.stockmarket.market.api.CreateOrderRequest;
import br.ufs.stockmarket.market.api.OrderResponse;
import br.ufs.stockmarket.market.domain.*;
import br.ufs.stockmarket.market.error.InvalidOrderException;
import br.ufs.stockmarket.market.error.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
public class OrderService {
    private static final List<OrderStatus> ACTIVE = List.of(OrderStatus.OPEN, OrderStatus.PARTIALLY_FILLED);
    private static final BigDecimal MAX_MONEY = new BigDecimal("999999999999999.9999");
    private final OrderRepository orderRepository;
    private final AssetRepository assetRepository;
    private final TradeRepository tradeRepository;
    private final LedgerService ledger;
    private final OrderRequestStore requestStore;
    private final OutboxStore outbox;
    private final MatchingEngine matchingEngine = new MatchingEngine();

    public OrderService(OrderRepository orderRepository, AssetRepository assetRepository,
                        TradeRepository tradeRepository, LedgerService ledger, OrderRequestStore requestStore,
                        OutboxStore outbox) {
        this.orderRepository = orderRepository;
        this.assetRepository = assetRepository;
        this.tradeRepository = tradeRepository;
        this.ledger = ledger;
        this.requestStore = requestStore;
        this.outbox = outbox;
    }

    @Transactional
    public OrderResponse create(UUID userId, CreateOrderRequest request, String requestKey) {
        BigDecimal price = validatePrice(request.price(), request.quantity());
        if (requestKey != null) {
            if (!requestKey.matches("[A-Za-z0-9._:-]{1,128}")) {
                throw new InvalidOrderException("Idempotency-Key inválida.");
            }
            // INSERT waits for a concurrent claim of the same user/key to commit or roll back.
            if (!requestStore.claim(userId, requestKey)) {
                OrderEntity prior = orderRepository.findByIdAndUserId(
                        requestStore.existingOrder(userId, requestKey), userId).orElseThrow();
                if (!prior.getAssetId().equals(request.assetId()) || prior.getSide() != request.side()
                        || prior.getPrice().compareTo(price) != 0 || prior.getQuantity() != request.quantity()) {
                    throw new InvalidOrderException("Idempotency-Key já utilizada com outros dados.");
                }
                return OrderResponse.from(prior, tickerFor(prior.getAssetId()));
            }
        }
        AssetEntity asset = assetRepository.findByIdForUpdate(request.assetId())
                .orElseThrow(() -> new NotFoundException("Ativo não encontrado."));
        if (!asset.isActive()) {
            throw new InvalidOrderException("Ativo não está disponível para negociação.");
        }

        // Serialização por ativo; a sequência SQL é atribuída sob este bloqueio.
        OrderEntity taker = orderRepository.saveAndFlush(new OrderEntity(
                userId, asset.getId(), request.side(), price, request.quantity()));
        List<OrderEntity> candidates = request.side() == OrderSide.BUY
                ? orderRepository.findAllByAssetIdAndSideAndStatusInAndPriceLessThanEqualOrderByPriceAscSequenceNumberAsc(
                        asset.getId(), OrderSide.SELL, ACTIVE, price)
                : orderRepository.findAllByAssetIdAndSideAndStatusInAndPriceGreaterThanEqualOrderByPriceDescSequenceNumberAsc(
                        asset.getId(), OrderSide.BUY, ACTIVE, price);

        Map<UUID, OrderEntity> makers = new HashMap<>();
        List<MatchingEngine.OrderView> views = new ArrayList<>();
        for (OrderEntity maker : candidates) {
            makers.put(maker.getId(), maker);
            views.add(view(maker, Objects.requireNonNull(maker.getSequenceNumber())));
        }
        MatchingEngine.Plan plan;
        try {
            // O taker não participa do desempate entre makers.
            plan = matchingEngine.plan(view(taker, 0L), views);
        } catch (MatchingEngine.SelfTradeException ex) {
            throw new InvalidOrderException(ex.getMessage());
        }

        // Antes de reservar ou liquidar, bloqueia TODOS os participantes em ordem global.
        Set<UUID> participants = new HashSet<>();
        participants.add(userId);
        for (MatchingEngine.Match match : plan.matches()) {
            participants.add(makers.get(match.makerOrderId()).getUserId());
        }
        ledger.lockParticipants(asset.getId(), participants);
        if (taker.getSide() == OrderSide.BUY) {
            ledger.reserveBuy(taker.getId(), userId, asset.getId(), price, taker.getQuantity());
        } else {
            ledger.reserveSell(taker.getId(), userId, asset.getId(), price, taker.getQuantity());
        }

        for (MatchingEngine.Match match : plan.matches()) {
            OrderEntity maker = makers.get(match.makerOrderId());
            OrderEntity buy = taker.getSide() == OrderSide.BUY ? taker : maker;
            OrderEntity sell = taker.getSide() == OrderSide.SELL ? taker : maker;
            TradeEntity trade = tradeRepository.saveAndFlush(new TradeEntity(asset.getId(),
                    buy.getId(), sell.getId(), buy.getUserId(), sell.getUserId(),
                    match.price(), match.quantity()));
            ledger.settleTrade(new TradeSettlement(trade.getId(), asset.getId(),
                    buy.getId(), sell.getId(), buy.getUserId(), sell.getUserId(),
                    match.price(), match.quantity()));
            buy.fill(match.quantity());
            sell.fill(match.quantity());
            asset.updateCurrentPrice(match.price());
            outbox.tradeExecuted(trade, taker.getId());
        }
        if (taker.getRemainingQuantity() != plan.remainingQuantity()) {
            throw new IllegalStateException("Quantidade restante divergente do plano de matching.");
        }
        if (requestKey != null) {
            requestStore.complete(userId, requestKey, taker.getId());
        }
        // The same transaction commits orders, trades, ledger and outbox, or rolls them all back.
        outbox.orderChanged(taker, taker.getId());
        for (MatchingEngine.Match match : plan.matches()) {
            outbox.orderChanged(makers.get(match.makerOrderId()), taker.getId());
        }
        for (UUID participant : participants) {
            outbox.portfolioChanged(asset.getId(), participant, taker.getId());
        }
        outbox.bookChanged(asset.getId(), taker.getId());
        return OrderResponse.from(taker, asset.getTicker());
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> listMine(UUID userId) {
        return orderRepository.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(order -> OrderResponse.from(order, tickerFor(order.getAssetId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse findMine(UUID userId, UUID orderId) {
        OrderEntity order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new NotFoundException("Ordem não encontrada."));
        return OrderResponse.from(order, tickerFor(order.getAssetId()));
    }

    @Transactional
    public OrderResponse cancel(UUID userId, UUID orderId) {
        // Consulta escalar evita carregar a ordem antes de adquirir o bloqueio do ativo.
        UUID assetId = orderRepository.findAssetIdForOwner(orderId, userId)
                .orElseThrow(() -> new NotFoundException("Ordem não encontrada."));
        AssetEntity asset = assetRepository.findByIdForUpdate(assetId)
                .orElseThrow(() -> new NotFoundException("Ativo não encontrado."));
        OrderEntity order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new NotFoundException("Ordem não encontrada."));
        if (!order.isActive()) {
            throw new InvalidOrderException("A ordem não está aberta para cancelamento.");
        }
        ledger.lockParticipants(assetId, List.of(userId));
        ledger.releaseRemaining(order.getId());
        order.cancel();
        outbox.orderChanged(order, order.getId());
        outbox.portfolioChanged(assetId, userId, order.getId());
        outbox.bookChanged(assetId, order.getId());
        return OrderResponse.from(order, asset.getTicker());
    }

    private MatchingEngine.OrderView view(OrderEntity order, long sequence) {
        return new MatchingEngine.OrderView(order.getId(), order.getUserId(), order.getAssetId(),
                order.getSide(), order.getPrice(), order.getRemainingQuantity(), sequence);
    }

    private BigDecimal validatePrice(BigDecimal price, long quantity) {
        if (price == null || price.signum() <= 0 || quantity <= 0) {
            throw new InvalidOrderException("Preço e quantidade devem ser positivos.");
        }
        try {
            BigDecimal normalized = price.setScale(4, RoundingMode.UNNECESSARY);
            if (normalized.multiply(BigDecimal.valueOf(quantity)).compareTo(MAX_MONEY) > 0) {
                throw new InvalidOrderException("Valor total da ordem excede o limite monetário.");
            }
            return normalized;
        } catch (ArithmeticException ex) {
            throw new InvalidOrderException("Preço deve ter até quatro casas decimais.");
        }
    }

    private String tickerFor(UUID assetId) {
        return assetRepository.findById(assetId).map(AssetEntity::getTicker).orElse("UNKNOWN");
    }
}
