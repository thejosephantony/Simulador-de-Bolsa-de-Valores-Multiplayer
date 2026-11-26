package br.ufs.stockmarket.portfolio.application;

import br.ufs.stockmarket.ledger.LedgerService;
import br.ufs.stockmarket.portfolio.api.PortfolioResponse;
import br.ufs.stockmarket.portfolio.api.PositionResponse;
import br.ufs.stockmarket.portfolio.domain.AccountEntity;
import br.ufs.stockmarket.portfolio.domain.AccountRepository;
import br.ufs.stockmarket.portfolio.domain.PortfolioPositionEntity;
import br.ufs.stockmarket.portfolio.domain.PortfolioPositionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class PortfolioService {

    private static final Logger log = LoggerFactory.getLogger(PortfolioService.class);

    private final AccountRepository accountRepository;
    private final PortfolioPositionRepository positionRepository;
    private final LedgerService ledgerService;
    private final JdbcClient jdbc;

    public PortfolioService(
            AccountRepository accountRepository,
            PortfolioPositionRepository positionRepository,
            LedgerService ledgerService,
            DataSource dataSource
    ) {
        this.accountRepository = accountRepository;
        this.positionRepository = positionRepository;
        this.ledgerService = ledgerService;
        this.jdbc = JdbcClient.create(dataSource);
    }

    @Transactional
    public PortfolioResponse getOrCreate(UUID userId) {
        ledgerService.ensureAccount(userId);
        AccountEntity account = accountRepository.findByUserId(userId).orElseThrow();
        Map<UUID, AssetQuote> quotes = loadQuotes();

        var positions = positionRepository.findAllByUserIdOrderByAssetIdAsc(userId)
                .stream()
                .filter(position -> position.getQuantity() > 0 || position.getReservedQuantity() > 0)
                .map(position -> toResponse(position, quotes.get(position.getAssetId())))
                .toList();

        BigDecimal positionsValue = positions.stream()
                .map(PositionResponse::marketValue)
                .filter(value -> value != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new PortfolioResponse(
                userId,
                account.getBalance(),
                account.getReservedBalance(),
                account.getBalance().subtract(account.getReservedBalance()),
                positionsValue,
                account.getBalance().add(positionsValue),
                positions
        );
    }

    private static PositionResponse toResponse(PortfolioPositionEntity position, AssetQuote quote) {
        BigDecimal price = quote == null ? null : quote.currentPrice();
        BigDecimal quantity = BigDecimal.valueOf(position.getQuantity());
        BigDecimal marketValue = price == null ? null : price.multiply(quantity);
        BigDecimal pnl = price == null ? null
                : price.subtract(position.getAveragePrice()).multiply(quantity);
        return new PositionResponse(
                position.getAssetId(),
                quote == null ? null : quote.ticker(),
                position.getQuantity(),
                position.getReservedQuantity(),
                position.getQuantity() - position.getReservedQuantity(),
                position.getAveragePrice(),
                price,
                marketValue,
                pnl
        );
    }

    /**
     * Leitura do preço atual no schema do Market (mesmo banco). Somente leitura; se o Market ainda não
     * criou a tabela, a carteira é devolvida sem valores de mercado. A existência é checada antes porque,
     * no PostgreSQL, um erro de SQL abortaria a transação inteira (incluindo o ensureAccount).
     */
    private Map<UUID, AssetQuote> loadQuotes() {
        Map<UUID, AssetQuote> quotes = new HashMap<>();
        boolean marketReady = jdbc.sql("SELECT to_regclass('market.assets') IS NOT NULL")
                .query(Boolean.class)
                .single();
        if (!marketReady) {
            log.warn("Tabela market.assets ainda não existe; carteira sem preços de mercado.");
            return quotes;
        }
        jdbc.sql("SELECT id, ticker, current_price FROM market.assets")
                .query((rs, n) -> new AssetQuote(
                        rs.getObject("id", UUID.class), rs.getString("ticker"), rs.getBigDecimal("current_price")))
                .list()
                .forEach(quote -> quotes.put(quote.assetId(), quote));
        return quotes;
    }

    private record AssetQuote(UUID assetId, String ticker, BigDecimal currentPrice) {
    }
}
