package br.ufs.stockmarket.agent.api;

import br.ufs.stockmarket.agent.config.AgentsProperties;
import br.ufs.stockmarket.agent.engine.AgentManager;
import br.ufs.stockmarket.agent.engine.AgentRunner;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Controle dos agentes para a demonstração. Consultar é livre; pausar e retomar exigem o header
 * {@code X-Admin-Token} igual a AGENT_ADMIN_TOKEN. Não há endpoint que altere saldo, carteira ou ordens:
 * os agentes só negociam pela API pública do Market.
 */
@RestController
@RequestMapping("/api/v1/agents")
public class AgentController {

    private final AgentManager manager;
    private final byte[] adminToken;

    public AgentController(AgentManager manager, AgentsProperties properties) {
        this.manager = manager;
        this.adminToken = properties.adminToken().getBytes(StandardCharsets.UTF_8);
    }

    @GetMapping
    public List<AgentRunner.AgentStatus> list() {
        return manager.statuses();
    }

    @PostMapping("/{name}/pause")
    public AgentRunner.AgentStatus pause(@PathVariable String name,
                                         @RequestHeader(value = "X-Admin-Token", required = false) String token) {
        AgentRunner runner = authorize(name, token);
        runner.pause();
        return runner.status();
    }

    @PostMapping("/{name}/resume")
    public AgentRunner.AgentStatus resume(@PathVariable String name,
                                          @RequestHeader(value = "X-Admin-Token", required = false) String token) {
        AgentRunner runner = authorize(name, token);
        runner.resume();
        return runner.status();
    }

    private AgentRunner authorize(String name, String token) {
        if (token == null || !MessageDigest.isEqual(adminToken, token.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "X-Admin-Token inválido.");
        }
        return manager.find(name)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agente não encontrado."));
    }
}
