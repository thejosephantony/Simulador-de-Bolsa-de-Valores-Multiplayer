package br.ufs.stockmarket.agent.client;

import br.ufs.stockmarket.agent.client.ApiModels.Asset;
import br.ufs.stockmarket.agent.client.ApiModels.LoginResponse;
import br.ufs.stockmarket.agent.client.ApiModels.NewOrder;
import br.ufs.stockmarket.agent.client.ApiModels.Order;
import br.ufs.stockmarket.agent.client.ApiModels.Portfolio;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Sessão de um agente na API pública: o mesmo caminho de um investidor humano
 * (login no Auth, JWT no Authorization, ordens em /api/v1/orders com Idempotency-Key).
 */
public class TradingClient {

    private final RestClient auth;
    private final RestClient market;
    private final RestClient portfolio;
    private final String username;
    private final String password;

    private String token;
    private Instant tokenExpiresAt = Instant.EPOCH;

    public TradingClient(RestClient.Builder builder, String authUrl, String marketUrl, String portfolioUrl,
                         String username, String password) {
        this.auth = builder.clone().baseUrl(authUrl).build();
        this.market = builder.clone().baseUrl(marketUrl).build();
        this.portfolio = builder.clone().baseUrl(portfolioUrl).build();
        this.username = username;
        this.password = password;
    }

    public List<Asset> assets() {
        return market.get().uri("/api/v1/assets").retrieve().body(new ParameterizedTypeReference<List<Asset>>() {
        });
    }

    public List<Order> myOrders() {
        return authenticated(() -> market.get().uri("/api/v1/orders")
                .header("Authorization", bearer())
                .retrieve().body(new ParameterizedTypeReference<List<Order>>() {
                }));
    }

    public Portfolio myPortfolio() {
        return authenticated(() -> portfolio.get().uri("/api/v1/portfolio/me")
                .header("Authorization", bearer())
                .retrieve().body(Portfolio.class));
    }

    /** Envia a ordem com uma chave de idempotência nova; repetir a mesma chave não duplica a ordem. */
    public Order placeOrder(NewOrder order) {
        String key = UUID.randomUUID().toString();
        return authenticated(() -> market.post().uri("/api/v1/orders")
                .header("Authorization", bearer())
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .body(order)
                .retrieve().body(Order.class));
    }

    public void cancel(UUID orderId) {
        authenticated(() -> market.delete().uri("/api/v1/orders/{id}", orderId)
                .header("Authorization", bearer())
                .retrieve().toBodilessEntity());
    }

    private <T> T authenticated(Supplier<T> call) {
        ensureToken();
        try {
            return call.get();
        } catch (HttpClientErrorException.Unauthorized expired) {
            token = null;
            ensureToken();
            return call.get();
        }
    }

    private void ensureToken() {
        if (token != null && Instant.now().isBefore(tokenExpiresAt.minusSeconds(60))) {
            return;
        }
        LoginResponse login = auth.post().uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("identifier", username, "password", password))
                .retrieve().body(LoginResponse.class);
        token = login.accessToken();
        tokenExpiresAt = Instant.now().plusSeconds(login.expiresInSeconds());
    }

    private String bearer() {
        return "Bearer " + token;
    }
}
