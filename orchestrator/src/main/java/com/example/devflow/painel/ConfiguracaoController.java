package com.example.devflow.painel;

import com.example.devflow.DevFlowProperties;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ConfiguracaoController {

    private final DevFlowProperties properties;

    public ConfiguracaoController(DevFlowProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/api/configuracao")
    public Map<String, Object> configuracao() {
        return Map.of("abrirPullRequest", properties.abrirPullRequest());
    }
}
