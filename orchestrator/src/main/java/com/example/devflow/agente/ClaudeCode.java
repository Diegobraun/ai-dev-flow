package com.example.devflow.agente;

import com.example.devflow.DevFlowProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class ClaudeCode implements Agente {

    private static final Logger log = LoggerFactory.getLogger(ClaudeCode.class);

    private final DevFlowProperties properties;
    private final ObjectMapper json;

    public ClaudeCode(DevFlowProperties properties, ObjectMapper json) {
        this.properties = properties;
        this.json = json;
    }

    @Override
    public ResultadoDoAgente executar(ChamadaDoAgente chamada) {
        List<String> comando = comando(chamada.etapa());
        Path prompt = arquivo(chamada.log(), ".prompt.md");
        Path saida = arquivo(chamada.log(), ".json");
        Path erros = arquivo(chamada.log(), ".err");
        long inicio = System.nanoTime();
        try {
            Files.createDirectories(chamada.log().getParent());
            Files.writeString(prompt, chamada.prompt());
            log.info("{} em {}", chamada.etapa().agente(), chamada.workspace());
            Process processo = new ProcessBuilder(comando)
                    .directory(chamada.workspace().toFile())
                    .redirectInput(prompt.toFile())
                    .redirectOutput(saida.toFile())
                    .redirectError(erros.toFile())
                    .start();
            if (!processo.waitFor(properties.timeout().toMillis(), TimeUnit.MILLISECONDS)) {
                processo.destroyForcibly();
                throw new AgenteFalhou(chamada.etapa().agente() + " passou de " + properties.timeout());
            }
            Duration duracao = Duration.ofNanos(System.nanoTime() - inicio);
            return interpretar(chamada.etapa(), processo.exitValue(), Files.readString(saida), Files.readString(erros), duracao);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AgenteFalhou(chamada.etapa().agente() + " interrompido", e);
        }
    }

    List<String> comando(Etapa etapa) {
        Path plugin = properties.plugin().toAbsolutePath().normalize();
        List<String> comando = new ArrayList<>(List.of(
                properties.claude(), "-p",
                "--agent", "devflow:" + etapa.agente(),
                "--plugin-dir", plugin.toString(),
                "--output-format", "json",
                "--json-schema", schema(etapa),
                "--permission-mode", etapa.modoDePermissao(),
                "--setting-sources", "project",
                "--strict-mcp-config",
                "--no-session-persistence",
                "--max-budget-usd", properties.orcamentoPorEtapaUsd().toPlainString()));
        if (properties.mcpConfig() != null && !properties.mcpConfig().isBlank()) {
            comando.addAll(List.of("--mcp-config", Path.of(properties.mcpConfig()).toAbsolutePath().toString()));
        }
        String modelo = properties.modelo(etapa.agente());
        if (modelo != null && !modelo.isBlank()) {
            comando.addAll(List.of("--model", modelo));
        }
        comando.add("--allowedTools");
        comando.add("Read(/" + plugin + "/**)");
        comando.addAll(etapa.ferramentas());
        comando.add("--disallowedTools");
        comando.addAll(Etapa.PROIBIDAS);
        return comando;
    }

    ResultadoDoAgente interpretar(Etapa etapa, int codigo, String saida, String erros, Duration duracao) {
        JsonNode resultado;
        try {
            resultado = json.readTree(saida);
        } catch (IOException e) {
            throw new AgenteFalhou(etapa.agente() + " terminou com código " + codigo + " sem JSON: " + resumo(erros + saida));
        }
        if (resultado == null || resultado.path("is_error").asBoolean(false) || codigo != 0) {
            String motivo = resultado == null ? erros : resultado.path("subtype").asText() + " " + resultado.path("result").asText();
            throw new AgenteFalhou(etapa.agente() + " falhou (código " + codigo + "): " + resumo(motivo));
        }
        JsonNode estruturado = resultado.path("structured_output");
        if (!estruturado.isObject()) {
            throw new AgenteFalhou(etapa.agente() + " não devolveu a saída estruturada: " + resumo(resultado.path("result").asText()));
        }
        return new ResultadoDoAgente(estruturado, resultado.path("total_cost_usd").asDouble(0), resultado.path("num_turns").asInt(0), duracao);
    }

    private String schema(Etapa etapa) {
        try {
            return new ClassPathResource(etapa.schema()).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path arquivo(Path base, String extensao) {
        return base.resolveSibling(base.getFileName() + extensao);
    }

    private static String resumo(String texto) {
        String limpo = texto == null ? "" : texto.strip();
        return limpo.length() > 500 ? limpo.substring(limpo.length() - 500) : limpo;
    }
}
