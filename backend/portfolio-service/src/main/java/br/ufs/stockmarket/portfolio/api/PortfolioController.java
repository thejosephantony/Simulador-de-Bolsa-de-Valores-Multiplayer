package br.ufs.stockmarket.portfolio.api;

import br.ufs.stockmarket.portfolio.application.PortfolioService;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/portfolio")
public class PortfolioController {

    private final PortfolioService portfolioService;

    public PortfolioController(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    @GetMapping("/me")
    public PortfolioResponse me(JwtAuthenticationToken authentication) {
        UUID userId = UUID.fromString(authentication.getToken().getSubject());
        return portfolioService.getOrCreate(userId);
    }
}
