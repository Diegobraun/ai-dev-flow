package com.example.devflow.agente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.devflow.DevFlowProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClaudeCodeTest {

    @TempDir
    Path temp;

    @Test
    void montaOComandoComAsPermissoesDaEtapa() {
        ClaudeCode claude = claude("claude", "", Map.of("revisor", "opus"));

        List<String> comando = claude.comando(Etapa.REVISAO);

        assertThat(comando).containsSubsequence("claude", "-p", "--agent", "devflow:revisor");
        assertThat(comando).containsSubsequence("--setting-sources", "project", "--strict-mcp-config");
        assertThat(comando).containsSubsequence("--model", "opus");
        assertThat(comando).contains("Edit(.devflow/**)", "Bash(git diff *)");
        assertThat(comando).contains("Bash(mvn *)").doesNotContain("Edit", "Write", "Bash(git commit *)", "--mcp-config");
        assertThat(comando.subList(comando.indexOf("--disallowedTools"), comando.size())).contains("Bash(git push *)");
        assertThat(comando.get(comando.indexOf("--json-schema") + 1)).contains("veredito");
    }

    @Test
    void desenvolvedorEditaERodaBuild() {
        List<String> comando = claude("claude", "mcp.json", Map.of()).comando(Etapa.DESENVOLVIMENTO);

        assertThat(comando).containsSubsequence("--permission-mode", "acceptEdits");
        assertThat(comando).contains("Edit", "Write", "Bash(mvn *)", "Bash(git commit *)");
        assertThat(comando).containsSubsequence("--mcp-config", temp.resolve("mcp.json").toString());
        assertThat(comando).doesNotContain("--model");
    }

    @Test
    void usaOMcpDoRepositorioQuandoNaoHaConfiguracao() throws IOException {
        Path workspace = Files.createDirectories(temp.resolve("repo"));
        Files.writeString(workspace.resolve(".mcp.json"), "{\"mcpServers\":{}}");

        assertThat(claude("claude", "", Map.of()).comando(Etapa.REFINAMENTO, workspace))
                .containsSubsequence("--mcp-config", workspace.resolve(".mcp.json").toString());
        assertThat(claude("claude", "mcp.json", Map.of()).comando(Etapa.REFINAMENTO, workspace))
                .containsSubsequence("--mcp-config", temp.resolve("mcp.json").toString());
        assertThat(claude("claude", "", Map.of()).comando(Etapa.REFINAMENTO, temp.resolve("sem-mcp")))
                .doesNotContain("--mcp-config");
    }

    @Test
    void testadorSoEditaTestes() {
        List<String> comando = claude("claude", "", Map.of()).comando(Etapa.TESTE);

        assertThat(comando).contains("Edit(src/test/**)", "Bash(mvn *)").doesNotContain("Edit", "Write");
    }

    @Test
    void executaOClaudeNoWorkspaceELeASaidaEstruturada() throws IOException {
        Path script = falso("""
                #!/bin/sh
                prompt=$(cat)
                echo "$PWD|$prompt" > chamado.txt
                echo '{"type":"result","subtype":"success","is_error":false,"num_turns":7,"total_cost_usd":0.42,"result":"ok","structured_output":{"veredito":"bloqueado","bloqueantes":2,"importantes":0,"resumo":"B1"}}'
                """);
        Path workspace = Files.createDirectories(temp.resolve("ws"));

        ResultadoDoAgente resultado = claude(script.toString(), "", Map.of())
                .executar(new ChamadaDoAgente(Etapa.REVISAO, workspace, "revise", workspace.resolve(".devflow/t/logs/revisao-1")));

        assertThat(resultado.texto("veredito")).isEqualTo("bloqueado");
        assertThat(resultado.inteiro("bloqueantes")).isEqualTo(2);
        assertThat(resultado.custoUsd()).isEqualTo(0.42);
        assertThat(resultado.turnos()).isEqualTo(7);
        assertThat(Files.readString(workspace.resolve("chamado.txt")).strip()).endsWith("|revise");
        assertThat(workspace.resolve(".devflow/t/logs/revisao-1.prompt.md")).hasContent("revise");
        assertThat(workspace.resolve(".devflow/t/logs/revisao-1.json")).exists();
    }

    @Test
    void falhaQuandoOClaudeTerminaComErro() throws IOException {
        Path script = falso("""
                #!/bin/sh
                cat > /dev/null
                echo '{"type":"result","subtype":"error_max_budget_usd","is_error":true,"result":"budget"}'
                exit 1
                """);
        Path workspace = Files.createDirectories(temp.resolve("ws"));

        assertThatThrownBy(() -> claude(script.toString(), "", Map.of())
                .executar(new ChamadaDoAgente(Etapa.TESTE, workspace, "teste", workspace.resolve("logs/teste-1"))))
                .isInstanceOf(AgenteFalhou.class)
                .hasMessageContaining("testador")
                .hasMessageContaining("error_max_budget_usd");
    }

    @Test
    void falhaSemSaidaEstruturada() {
        ClaudeCode claude = claude("claude", "", Map.of());

        assertThatThrownBy(() -> claude.interpretar(Etapa.REFINAMENTO, 0,
                "{\"is_error\":false,\"result\":\"texto solto\"}", "", Duration.ZERO))
                .isInstanceOf(AgenteFalhou.class)
                .hasMessageContaining("texto solto");
        assertThatThrownBy(() -> claude.interpretar(Etapa.REFINAMENTO, 1, "não é json", "boom", Duration.ZERO))
                .isInstanceOf(AgenteFalhou.class)
                .hasMessageContaining("boom");
    }

    private ClaudeCode claude(String binario, String mcp, Map<String, String> modelos) {
        DevFlowProperties properties = new DevFlowProperties(temp.resolve("ws"), temp.resolve("plugin"), binario,
                mcp.isEmpty() ? "" : temp.resolve(mcp).toString(), Duration.ofSeconds(30), BigDecimal.ONE, modelos, 3, false);
        return new ClaudeCode(properties, new ObjectMapper());
    }

    private Path falso(String conteudo) throws IOException {
        Path script = temp.resolve("claude-falso.sh");
        Files.writeString(script, conteudo);
        Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwxr-xr-x"));
        return script;
    }
}
