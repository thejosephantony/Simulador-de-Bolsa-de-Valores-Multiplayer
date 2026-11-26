package br.ufs.stockmarket.market.config;

import br.ufs.stockmarket.ledger.LedgerConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@Import(LedgerConfiguration.class)
public class LedgerImportConfig {
}
