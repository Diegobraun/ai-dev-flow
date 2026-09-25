package com.example.devflow.fluxo;

import com.example.devflow.DevFlowProperties;
import com.example.devflow.agente.Agente;
import com.example.devflow.agente.ChamadaDoAgente;
import com.example.devflow.agente.Etapa;
import com.example.devflow.agente.ResultadoDoAgente;
import com.example.devflow.workspace.Workspace;
import com.example.devflow.workspace.Workspaces;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.StreamSupport;
import org.springframework.stereotype.Component;

@Component
public class EtapasDoFluxo {

    private static final long UMA_HORA = 3_600_000L;
    private static final int LIMITE_DO_DOCUMENTO = 60_000;

    private static final TypeReference<List<Map<String, Object>>> AREAS = new TypeReference<>() {
    };

    private final Agente agente;
    private final Workspaces workspaces;
    private final DevFlowProperties properties;
    private final ObjectMapper json;

    public EtapasDoFluxo(Agente agente, Workspaces workspaces, DevFlowProperties properties, ObjectMapper json) {
        this.agente = agente;
        this.workspaces = workspaces;
        this.properties = properties;
        this.json = json;
    }

    @JobWorker(type = "preparar-workspace")
    public Map<String, Object> preparar(@Variable(name = "tarefa") String tarefa,
                                        @Variable(name = "repositorio") String repositorio,
                                        @Variable(name = "branchBase") String branchBase) {
        Workspace workspace = workspaces.preparar(tarefa, repositorio, branchBase);
        return Map.of(
                "workspace", workspace.diretorio().toString(),
                "branch", workspace.branch(),
                "commitBase", workspace.commitBase(),
                "commitAtual", workspace.commitBase(),
                "rodadaDeRefinamento", 0,
                "rodadaDeDesenvolvimento", 0,
                "rodadaDeRevisao", 0,
                "custoUsd", 0.0);
    }

    @JobWorker(type = "refinar", timeout = UMA_HORA, maxJobsActive = 2)
    public Map<String, Object> refinar(@Variable(name = "tarefa") String tarefa,
                                       @Variable(name = "descricao") String descricao,
                                       @Variable(name = "workspace") String workspace,
                                       @Variable(name = "rodadaDeRefinamento") int rodadaAnterior,
                                       @Variable(name = "observacoesDoRefinamento", optional = true) String observacoes,
                                       @Variable(name = "aprovacoesEntreAreas", optional = true) List<Map<String, Object>> aprovacoes,
                                       @Variable(name = "refinamentoAprovado", optional = true) Boolean aprovado,
                                       @Variable(name = "refinamentoStatus", optional = true) String statusAnterior,
                                       @Variable(name = "rodadaDeRevisao", optional = true) Integer rodadaDeRevisao,
                                       @Variable(name = "limiteDeRevisoes", optional = true) Integer limiteDeRevisoes,
                                       @Variable(name = "revisoesPorRefinamento", optional = true) Integer revisoesPorRefinamento,
                                       @Variable(name = "custoUsd") double custo) {
        Path diretorio = Path.of(workspace);
        int rodada = rodadaAnterior + 1;
        boolean aceitarSugestoes = Boolean.TRUE.equals(aprovado) && "com-perguntas".equals(statusAnterior);
        ResultadoDoAgente resultado = executar(Etapa.REFINAMENTO, diretorio, tarefa, "refinamento-" + rodada,
                Prompts.refinamento(tarefa, descricao, rodada > 1 ? observacoes : null, recusas(aprovacoes), aceitarSugestoes));
        int porRefinamento = revisoesPorRefinamento != null ? revisoesPorRefinamento
                : limiteDeRevisoes != null ? limiteDeRevisoes : properties.limiteDeRevisoes();
        Map<String, Object> variaveis = new HashMap<>();
        variaveis.put("rodadaDeRefinamento", rodada);
        variaveis.put("refinamentoStatus", resultado.texto("status"));
        variaveis.put("refinamentoResumo", resultado.texto("resumo"));
        variaveis.put("refinamentoPerguntas", lista(resultado));
        variaveis.put("refinamentoDocumento", documento(diretorio, tarefa, "refinamento.md"));
        variaveis.put("refinamentoAprovado", false);
        variaveis.put("areasAfetadas", areas(resultado));
        variaveis.put("observacoesDoRefinamento", null);
        variaveis.put("aprovacoesEntreAreas", null);
        variaveis.put("correcao", null);
        variaveis.put("revisoesPorRefinamento", porRefinamento);
        variaveis.put("limiteDeRevisoes", (rodadaDeRevisao == null ? 0 : rodadaDeRevisao) + porRefinamento);
        variaveis.put("custoUsd", somar(custo, resultado));
        return variaveis;
    }

    @JobWorker(type = "desenvolver", timeout = UMA_HORA, maxJobsActive = 1)
    public Map<String, Object> desenvolver(@Variable(name = "tarefa") String tarefa,
                                           @Variable(name = "workspace") String workspace,
                                           @Variable(name = "commitAtual") String commitAtual,
                                           @Variable(name = "rodadaDeDesenvolvimento") int rodadaAnterior,
                                           @Variable(name = "correcao", optional = true) String correcao,
                                           @Variable(name = "custoUsd") double custo) {
        Path diretorio = Path.of(workspace);
        workspaces.restaurar(diretorio, commitAtual);
        int rodada = rodadaAnterior + 1;
        ResultadoDoAgente resultado = executar(Etapa.DESENVOLVIMENTO, diretorio, tarefa, "desenvolvimento-" + rodada,
                Prompts.desenvolvimento(tarefa, rodada, correcao));
        Map<String, Object> variaveis = new HashMap<>();
        boolean impedido = "impedido".equals(resultado.texto("status"));
        variaveis.put("rodadaDeDesenvolvimento", rodada);
        variaveis.put("desenvolvimentoStatus", impedido ? "impedido" : "implementado");
        variaveis.put("desenvolvimentoBuild", resultado.texto("build"));
        if (impedido) {
            variaveis.put("observacoesDoRefinamento", "O desenvolvedor não conseguiu implementar o refinamento:\n" + resultado.texto("resumo"));
        }
        variaveis.put("desenvolvimentoResumo", resultado.texto("resumo"));
        variaveis.put("commitAtual", workspaces.head(diretorio));
        variaveis.put("correcao", null);
        variaveis.put("custoUsd", somar(custo, resultado));
        return variaveis;
    }

    @JobWorker(type = "revisar", timeout = UMA_HORA, maxJobsActive = 2)
    public Map<String, Object> revisar(@Variable(name = "tarefa") String tarefa,
                                       @Variable(name = "workspace") String workspace,
                                       @Variable(name = "commitBase") String commitBase,
                                       @Variable(name = "commitAtual") String commitAtual,
                                       @Variable(name = "rodadaDeRevisao") int rodadaAnterior,
                                       @Variable(name = "areasAfetadas", optional = true) List<Map<String, Object>> areas,
                                       @Variable(name = "custoUsd") double custo) {
        Path diretorio = Path.of(workspace);
        workspaces.restaurar(diretorio, commitAtual);
        int rodada = rodadaAnterior + 1;
        String arquivo = "review-" + rodada + ".md";
        ResultadoDoAgente resultado = executar(Etapa.REVISAO, diretorio, tarefa, "revisao-" + rodada,
                Prompts.revisao(tarefa, commitBase, rodada, nomes(areas)));
        String veredito = resultado.texto("veredito");
        Map<String, Object> variaveis = new HashMap<>();
        variaveis.put("rodadaDeRevisao", rodada);
        variaveis.put("veredito", veredito);
        variaveis.put("bloqueantes", resultado.inteiro("bloqueantes"));
        variaveis.put("reviewResumo", resultado.texto("resumo"));
        variaveis.put("reviewDocumento", documento(diretorio, tarefa, arquivo));
        variaveis.put("correcao", "bloqueado".equals(veredito) ? arquivo : null);
        variaveis.put("custoUsd", somar(custo, resultado));
        return variaveis;
    }

    @JobWorker(type = "testar", timeout = UMA_HORA, maxJobsActive = 1)
    public Map<String, Object> testar(@Variable(name = "tarefa") String tarefa,
                                      @Variable(name = "workspace") String workspace,
                                      @Variable(name = "commitAtual") String commitAtual,
                                      @Variable(name = "rodadaDeDesenvolvimento") int rodada,
                                      @Variable(name = "custoUsd") double custo) {
        Path diretorio = Path.of(workspace);
        workspaces.restaurar(diretorio, commitAtual);
        ResultadoDoAgente resultado = executar(Etapa.TESTE, diretorio, tarefa, "teste-" + rodada,
                Prompts.teste(tarefa));
        boolean aprovados = "aprovado".equals(resultado.texto("resultado"));
        Map<String, Object> variaveis = new HashMap<>();
        variaveis.put("testesAprovados", aprovados);
        variaveis.put("testesResumo", resultado.texto("resumo"));
        variaveis.put("testesDocumento", documento(diretorio, tarefa, "testes.md"));
        variaveis.put("commitAtual", workspaces.head(diretorio));
        variaveis.put("correcao", aprovados ? null : "testes.md");
        variaveis.put("custoUsd", somar(custo, resultado));
        return variaveis;
    }

    @JobWorker(type = "abrir-pr")
    public Map<String, Object> abrirPullRequest(@Variable(name = "tarefa") String tarefa,
                                                @Variable(name = "descricao") String descricao,
                                                @Variable(name = "workspace") String workspace,
                                                @Variable(name = "branch") String branch,
                                                @Variable(name = "branchBase") String branchBase,
                                                @Variable(name = "commitBase") String commitBase,
                                                @Variable(name = "rodadaDeRevisao") int rodadas,
                                                @Variable(name = "custoUsd") double custo) {
        Path diretorio = Path.of(workspace);
        String corpo = PullRequest.corpo(descricao,
                workspaces.documento(diretorio, tarefa, "refinamento.md"),
                workspaces.documento(diretorio, tarefa, "review-" + rodadas + ".md"),
                workspaces.documento(diretorio, tarefa, "testes.md"),
                rodadas, custo);
        Path arquivo = workspaces.escrever(diretorio, tarefa, "pr.md", corpo);
        String url = "";
        if (properties.abrirPullRequest()) {
            url = workspaces.abrirPullRequest(new Workspace(diretorio, branch, commitBase), branchBase,
                    PullRequest.titulo(tarefa, descricao), arquivo);
        }
        return Map.of("prUrl", url, "prCorpo", arquivo.toString(),
                "commits", workspaces.commitsDesde(diretorio, commitBase));
    }

    private ResultadoDoAgente executar(Etapa etapa, Path diretorio, String tarefa, String nome, String prompt) {
        return agente.executar(new ChamadaDoAgente(etapa, diretorio, prompt, workspaces.log(diretorio, tarefa, nome)));
    }

    private String documento(Path diretorio, String tarefa, String nome) {
        String conteudo = PullRequest.semCabecalho(workspaces.documento(diretorio, tarefa, nome));
        return conteudo.length() > LIMITE_DO_DOCUMENTO ? conteudo.substring(0, LIMITE_DO_DOCUMENTO) : conteudo;
    }

    private List<Map<String, Object>> areas(ResultadoDoAgente resultado) {
        List<Map<String, Object>> areas = json.convertValue(resultado.saida().path("areasAfetadas"), AREAS);
        return areas == null ? List.of() : areas;
    }

    private static List<String> recusas(List<Map<String, Object>> aprovacoes) {
        if (aprovacoes == null) {
            return List.of();
        }
        return aprovacoes.stream()
                .filter(Objects::nonNull)
                .filter(a -> !Boolean.TRUE.equals(a.get("aprovado")))
                .map(a -> a.get("area") + ": " + Objects.requireNonNullElse(a.get("motivo"), "sem motivo"))
                .toList();
    }

    private static List<String> nomes(List<Map<String, Object>> areas) {
        if (areas == null) {
            return List.of();
        }
        return areas.stream().map(a -> String.valueOf(a.get("area"))).toList();
    }

    private static List<String> lista(ResultadoDoAgente resultado) {
        return StreamSupport.stream(resultado.saida().path("perguntas").spliterator(), false)
                .map(no -> no.asText())
                .toList();
    }

    private static double somar(double custo, ResultadoDoAgente resultado) {
        return BigDecimal.valueOf(custo + resultado.custoUsd()).setScale(4, RoundingMode.HALF_UP).doubleValue();
    }
}
