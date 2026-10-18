package br.ufs.stockmarket.portfolio.application;

import br.ufs.stockmarket.portfolio.api.PortfolioResponse;
import br.ufs.stockmarket.portfolio.api.PositionResponse;
import br.ufs.stockmarket.portfolio.domain.AccountEntity;
import br.ufs.stockmarket.portfolio.domain.AccountRepository;
import br.ufs.stockmarket.portfolio.domain.PortfolioPositionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class PortfolioService {

    private final AccountRepository accountRepository;
    private final PortfolioPositionRepository positionRepository;
    private final BigDecimal initialBalance;

    public PortfolioService(
            AccountRepository accountRepository,
            PortfolioPositionRepository positionRepository,
            @Value("${portfolio.initial-balance}") BigDecimal initialBalance
    ) {
        this.accountRepository = accountRepository;
        this.positionRepository = positionRepository;
        this.initialBalance = initialBalance;
    }

    @Transactional
    public PortfolioResponse getOrCreate(UUID userId) {
        AccountEntity account = accountRepository.findByUserId(userId)
                .orElseGet(() -> accountRepository.save(new AccountEntity(userId, initialBalance)));

        var positions = positionRepository.findAllByUserIdOrderByAssetIdAsc(userId)
                .stream()
                .map(PositionResponse::from)
                .toList();

        return new PortfolioResponse(
                userId,
                account.getBalance(),
                account.getReservedBalance(),
                account.getBalance().subtract(account.getReservedBalance()),
                positions
        );
    }
}
