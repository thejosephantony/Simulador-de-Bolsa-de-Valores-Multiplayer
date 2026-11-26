package br.ufs.stockmarket.ledger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

import javax.sql.DataSource;
import java.math.BigDecimal;

/**
 * Uso: {@code @Import(LedgerConfiguration.class)} numa classe de configuração do serviço.
 * Usa o mesmo DataSource do serviço, portanto participa da transação Spring corrente
 * (JpaTransactionManager ou DataSourceTransactionManager).
 */
@Configuration(proxyBeanMethods = false)
public class LedgerConfiguration {

    @Bean
    LedgerService ledgerService(
            DataSource dataSource,
            @Value("${ledger.initial-balance:${INITIAL_BALANCE:100000.00}}") BigDecimal initialBalance
    ) {
        return new LedgerService(JdbcClient.create(dataSource), initialBalance);
    }
}
