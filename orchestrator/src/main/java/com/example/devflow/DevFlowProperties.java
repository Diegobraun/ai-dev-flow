package com.example.devflow;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("devflow")
public record DevFlowProperties(
        @DefaultValue("./workspaces") Path workspaces,
        @DefaultValue("../plugin") Path plugin,
        @DefaultValue("claude") String claude,
        String mcpConfig,
        @DefaultValue("45m") Duration timeout,
        @DefaultValue("5") BigDecimal orcamentoPorEtapaUsd,
        @DefaultValue Map<String, String> modelos,
        @DefaultValue("3") int limiteDeRevisoes,
        @DefaultValue("false") boolean abrirPullRequest) {

    public String modelo(String agente) {
        return modelos.get(agente);
    }
}
