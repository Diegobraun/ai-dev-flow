package com.example.devflow.painel;

import com.example.devflow.DevFlowProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class Painel {

    static final String PROCESSO = "dev-flow";
    static final String APROVAR_ENTRE_AREAS = "aprovar-entre-areas";
    private static final int LIMITE_DE_TAREFAS = 200;
    private static final List<String> VARIAVEIS_DO_RESUMO = List.of("tarefa", "descricao", "repositorio", "branch",
            "custoUsd", "rodadaDeRefinamento", "rodadaDeDesenvolvimento", "rodadaDeRevisao", "limiteDeRevisoes",
            "prUrl", "aprovacao");
    private static final Set<String> TIPOS_IGNORADOS = Set.of("MULTI_INSTANCE_BODY", "PROCESS", "SEQUENCE_FLOW");

    private final CamundaRest camunda;
    private final ObjectMapper json;
    private final DevFlowProperties properties;

    public Painel(CamundaRest camunda, ObjectMapper json, DevFlowProperties properties) {
        this.camunda = camunda;
        this.json = json;
        this.properties = properties;
    }

    public List<ResumoDaTarefa> tarefas() {
        List<JsonNode> instancias = camunda.buscar("process-instances", Map.of("processDefinitionId", PROCESSO),
                "startDate", "DESC", LIMITE_DE_TAREFAS);
        if (instancias.isEmpty()) {
            return List.of();
        }
        List<String> chaves = instancias.stream().map(i -> i.path("processInstanceKey").asText()).toList();
        Map<String, Map<String, JsonNode>> variaveis = variaveisDoProcesso(camunda.buscarTodos("variables", Map.of(
                "processInstanceKey", Map.of("$in", chaves),
                "name", Map.of("$in", VARIAVEIS_DO_RESUMO))));
        Map<String, List<JsonNode>> ativos = porInstancia(camunda.buscarTodos("element-instances",
                Map.of("processDefinitionId", PROCESSO, "state", "ACTIVE")));
        Map<String, List<JsonNode>> finais = porInstancia(camunda.buscarTodos("element-instances",
                Map.of("processDefinitionId", PROCESSO, "type", "END_EVENT")));
        Map<String, List<Pendencia>> pendencias = pendenciasAbertas(null).stream()
                .collect(Collectors.groupingBy(Pendencia::processInstanceKey));
        return instancias.stream()
                .map(instancia -> {
                    String chave = instancia.path("processInstanceKey").asText();
                    return resumo(instancia, variaveis.getOrDefault(chave, Map.of()),
                            ativos.getOrDefault(chave, List.of()), finais.getOrDefault(chave, List.of()),
                            pendencias.getOrDefault(chave, List.of()));
                })
                .toList();
    }

    public DetalheDaTarefa tarefa(String chave) {
        JsonNode instancia = camunda.obter("process-instances/{chave}", chave);
        List<JsonNode> todas = completas(camunda.buscarTodos("variables", Map.of("processInstanceKey", chave)));
        Map<String, JsonNode> doProcesso = new LinkedHashMap<>();
        Map<String, Map<String, JsonNode>> locais = new HashMap<>();
        for (JsonNode variavel : todas) {
            String escopo = variavel.path("scopeKey").asText();
            if (chave.equals(escopo)) {
                doProcesso.put(variavel.path("name").asText(), valor(variavel));
            } else {
                locais.computeIfAbsent(escopo, e -> new LinkedHashMap<>()).put(variavel.path("name").asText(), valor(variavel));
            }
        }
        List<JsonNode> elementos = new ArrayList<>(camunda.buscarTodos("element-instances", Map.of("processInstanceKey", chave)));
        elementos.sort(Comparator.comparing(e -> e.path("startDate").asText("")));
        List<PassoDoHistorico> historico = elementos.stream()
                .filter(e -> !TIPOS_IGNORADOS.contains(e.path("type").asText()))
                .map(e -> new PassoDoHistorico(
                        e.path("elementInstanceKey").asText(),
                        e.path("elementId").asText(),
                        e.path("elementName").asText(e.path("elementId").asText()),
                        e.path("type").asText(),
                        Etapas.de(e.path("elementId").asText()),
                        e.path("state").asText(),
                        texto(e, "startDate"),
                        texto(e, "endDate"),
                        e.path("hasIncident").asBoolean(false)))
                .toList();
        List<Incidente> incidentes = camunda.buscarTodos("incidents", Map.of("processInstanceKey", chave, "state", "ACTIVE"))
                .stream()
                .map(i -> new Incidente(i.path("incidentKey").asText(), i.path("elementId").asText(),
                        i.path("errorType").asText(), i.path("errorMessage").asText(), texto(i, "creationTime")))
                .toList();
        List<Pendencia> pendencias = pendenciasAbertas(chave, doProcesso, locais);
        List<JsonNode> ativos = elementos.stream().filter(e -> "ACTIVE".equals(e.path("state").asText())).toList();
        List<JsonNode> finais = elementos.stream().filter(e -> "END_EVENT".equals(e.path("type").asText())).toList();
        ResumoDaTarefa resumo = resumo(instancia, doProcesso, ativos, finais, pendencias);
        return new DetalheDaTarefa(resumo, doProcesso, historico, incidentes, pendencias,
                areas(doProcesso, locais, historico, pendencias), documentoDoPullRequest(doProcesso));
    }

    public List<Pendencia> pendencias(String grupo) {
        return pendenciasAbertas(grupo);
    }

    public Pendencia pendencia(String userTaskKey) {
        JsonNode tarefa = camunda.obter("user-tasks/{chave}", userTaskKey);
        String processo = tarefa.path("processInstanceKey").asText();
        String escopo = tarefa.path("elementInstanceKey").asText();
        Map<String, JsonNode> variaveis = new LinkedHashMap<>();
        Map<String, JsonNode> locais = new LinkedHashMap<>();
        for (JsonNode variavel : completas(camunda.buscarTodos("variables", Map.of("processInstanceKey", processo)))) {
            String escopoDaVariavel = variavel.path("scopeKey").asText();
            if (processo.equals(escopoDaVariavel)) {
                variaveis.put(variavel.path("name").asText(), valor(variavel));
            } else if (escopo.equals(escopoDaVariavel)) {
                locais.put(variavel.path("name").asText(), valor(variavel));
            }
        }
        variaveis.putAll(locais);
        return pendencia(tarefa, variaveis.get("tarefa"), variaveis.get("aprovacao"), variaveis);
    }

    public List<String> grupos() {
        Set<String> grupos = new TreeSet<>();
        pendenciasAbertas(null).forEach(p -> grupos.addAll(p.candidateGroups()));
        return List.copyOf(grupos);
    }

    public void concluir(String userTaskKey, Map<String, Object> variaveis) {
        JsonNode tarefa = camunda.obter("user-tasks/{chave}", userTaskKey);
        if (!"CREATED".equals(tarefa.path("state").asText("CREATED"))) {
            throw new PedidoInvalido("A pendência já foi concluída");
        }
        if (APROVAR_ENTRE_AREAS.equals(tarefa.path("elementId").asText())) {
            Object aprovado = variaveis.get("aprovado");
            if (!(aprovado instanceof Boolean)) {
                throw new PedidoInvalido("Informe se a área aprova");
            }
            Object motivo = variaveis.get("motivo");
            if (!(Boolean) aprovado && (motivo == null || motivo.toString().isBlank())) {
                throw new PedidoInvalido("O motivo é obrigatório para recusar");
            }
        }
        camunda.concluir(userTaskKey, variaveis);
    }

    private List<Pendencia> pendenciasAbertas(String grupo) {
        Map<String, Object> filtro = new LinkedHashMap<>();
        filtro.put("state", "CREATED");
        filtro.put("processDefinitionId", PROCESSO);
        if (grupo != null && !grupo.isBlank()) {
            filtro.put("candidateGroup", grupo);
        }
        List<JsonNode> tarefas = camunda.buscar("user-tasks", filtro, "creationDate", "DESC", CamundaRest.POR_PAGINA);
        if (tarefas.isEmpty()) {
            return List.of();
        }
        List<String> processos = tarefas.stream().map(t -> t.path("processInstanceKey").asText()).distinct().toList();
        Map<String, JsonNode> nomes = new HashMap<>();
        Map<String, JsonNode> aprovacoes = new HashMap<>();
        for (JsonNode variavel : camunda.buscarTodos("variables", Map.of(
                "processInstanceKey", Map.of("$in", processos),
                "name", Map.of("$in", List.of("tarefa", "aprovacao"))))) {
            if ("tarefa".equals(variavel.path("name").asText())) {
                nomes.put(variavel.path("processInstanceKey").asText(), valor(variavel));
            } else {
                aprovacoes.put(variavel.path("scopeKey").asText(), valor(variavel));
            }
        }
        return tarefas.stream()
                .map(t -> pendencia(t, nomes.get(t.path("processInstanceKey").asText()),
                        aprovacoes.get(t.path("elementInstanceKey").asText()), null))
                .toList();
    }

    private List<Pendencia> pendenciasAbertas(String processo, Map<String, JsonNode> doProcesso,
                                              Map<String, Map<String, JsonNode>> locais) {
        return camunda.buscar("user-tasks", Map.of("state", "CREATED", "processInstanceKey", processo),
                        "creationDate", "ASC", CamundaRest.POR_PAGINA)
                .stream()
                .map(t -> {
                    Map<String, JsonNode> local = locais.getOrDefault(t.path("elementInstanceKey").asText(), Map.of());
                    return pendencia(t, doProcesso.get("tarefa"), local.get("aprovacao"), null);
                })
                .toList();
    }

    private Pendencia pendencia(JsonNode tarefa, JsonNode nome, JsonNode aprovacao, Map<String, JsonNode> variaveis) {
        List<String> grupos = new ArrayList<>();
        tarefa.path("candidateGroups").forEach(g -> grupos.add(g.asText()));
        String area = aprovacao != null && aprovacao.hasNonNull("area") ? aprovacao.path("area").asText() : null;
        return new Pendencia(
                tarefa.path("userTaskKey").asText(),
                tarefa.path("elementId").asText(),
                tarefa.path("name").asText(tarefa.path("elementId").asText()),
                tarefa.path("processInstanceKey").asText(),
                tarefa.path("elementInstanceKey").asText(),
                nome != null && nome.isTextual() ? nome.asText() : null,
                grupos,
                texto(tarefa, "creationDate"),
                area,
                variaveis);
    }

    private ResumoDaTarefa resumo(JsonNode instancia, Map<String, JsonNode> variaveis, List<JsonNode> ativos,
                                  List<JsonNode> finais, List<Pendencia> pendencias) {
        List<JsonNode> ativosVisiveis = ativos.stream()
                .filter(e -> !TIPOS_IGNORADOS.contains(e.path("type").asText()))
                .toList();
        String estado = estado(instancia, finais);
        String etapa = switch (estado) {
            case "concluida", "cancelada" -> estado;
            default -> etapaAtual(ativosVisiveis, pendencias);
        };
        return new ResumoDaTarefa(
                instancia.path("processInstanceKey").asText(),
                textoDe(variaveis, "tarefa"),
                textoDe(variaveis, "descricao"),
                textoDe(variaveis, "repositorio"),
                textoDe(variaveis, "branch"),
                estado,
                etapa,
                ativosVisiveis.stream()
                        .map(e -> e.path("elementName").asText(e.path("elementId").asText()))
                        .distinct()
                        .toList(),
                numeroDe(variaveis, "custoUsd"),
                inteiroDe(variaveis, "rodadaDeRefinamento"),
                inteiroDe(variaveis, "rodadaDeDesenvolvimento"),
                inteiroDe(variaveis, "rodadaDeRevisao"),
                inteiroDe(variaveis, "limiteDeRevisoes"),
                textoDe(variaveis, "prUrl"),
                instancia.path("hasIncident").asBoolean(false),
                pendencias,
                texto(instancia, "startDate"),
                texto(instancia, "endDate"));
    }

    private static String estado(JsonNode instancia, List<JsonNode> finais) {
        return switch (instancia.path("state").asText()) {
            case "COMPLETED" -> finais.stream().anyMatch(f -> "tarefa-cancelada".equals(f.path("elementId").asText()))
                    ? "cancelada" : "concluida";
            case "TERMINATED", "CANCELED" -> "cancelada";
            default -> instancia.path("hasIncident").asBoolean(false) ? "incidente" : "ativa";
        };
    }

    private static String etapaAtual(List<JsonNode> ativos, List<Pendencia> pendencias) {
        for (Pendencia pendencia : pendencias) {
            if (Etapas.conhecida(pendencia.elementId())) {
                return Etapas.de(pendencia.elementId());
            }
        }
        return ativos.stream()
                .map(e -> e.path("elementId").asText())
                .filter(Etapas::conhecida)
                .map(Etapas::de)
                .findFirst()
                .orElse(ativos.isEmpty() ? "refinamento" : Etapas.OUTRA);
    }

    private List<AreaAfetada> areas(Map<String, JsonNode> doProcesso, Map<String, Map<String, JsonNode>> locais,
                                    List<PassoDoHistorico> historico, List<Pendencia> pendencias) {
        Map<String, JsonNode> porArea = new LinkedHashMap<>();
        Set<String> atuais = new HashSet<>();
        JsonNode afetadas = doProcesso.get("areasAfetadas");
        if (afetadas != null && afetadas.isArray()) {
            afetadas.forEach(a -> {
                if (a.hasNonNull("area")) {
                    porArea.put(a.path("area").asText(), a);
                    atuais.add(a.path("area").asText());
                }
            });
        }
        Map<String, List<String>> escoposPorArea = new HashMap<>();
        locais.forEach((escopo, variaveis) -> {
            JsonNode aprovacao = variaveis.get("aprovacao");
            if (aprovacao != null && aprovacao.hasNonNull("area")) {
                String area = aprovacao.path("area").asText();
                porArea.putIfAbsent(area, aprovacao);
                escoposPorArea.computeIfAbsent(area, a -> new ArrayList<>()).add(escopo);
            }
        });
        if (porArea.isEmpty()) {
            return List.of();
        }
        Map<String, JsonNode> respostas = respostasDasAreas(doProcesso);
        List<AreaAfetada> areas = new ArrayList<>();
        porArea.forEach((area, dados) -> {
            List<String> escopos = escoposPorArea.getOrDefault(area, List.of());
            JsonNode resposta = respostas.get(area);
            if (resposta == null && dados.has("aprovado")) {
                resposta = dados;
            }
            for (String escopo : escopos) {
                Map<String, JsonNode> local = locais.getOrDefault(escopo, Map.of());
                if (resposta == null && local.containsKey("aprovado")) {
                    ObjectNode respostaLocal = json.createObjectNode();
                    respostaLocal.set("aprovado", local.get("aprovado"));
                    respostaLocal.set("motivo", local.getOrDefault("motivo", NullNode.getInstance()));
                    resposta = respostaLocal;
                }
            }
            String status;
            String motivo = null;
            String userTaskKey = null;
            Pendencia aberta = pendencias.stream()
                    .filter(p -> area.equals(p.area()) || escopos.contains(p.elementInstanceKey()))
                    .findFirst()
                    .orElse(null);
            boolean retirada = afetadas != null && afetadas.isArray() && !atuais.contains(area);
            if (retirada) {
                status = "retirada";
                motivo = resposta != null && resposta.hasNonNull("motivo") ? resposta.path("motivo").asText() : null;
            } else if (aberta != null) {
                status = "pendente";
                userTaskKey = aberta.userTaskKey();
            } else if (resposta != null && resposta.path("aprovado").isBoolean()) {
                status = resposta.path("aprovado").asBoolean() ? "aprovada" : "recusada";
                motivo = resposta.hasNonNull("motivo") ? resposta.path("motivo").asText() : null;
            } else if (historico.stream().anyMatch(p -> escopos.contains(p.elementInstanceKey()) && "COMPLETED".equals(p.estado()))) {
                status = "respondida";
            } else {
                status = "aguardando";
            }
            areas.add(new AreaAfetada(area, dados.path("times"), dados.path("servicos"), dados.path("contratos"),
                    status, motivo, userTaskKey));
        });
        return areas;
    }

    private static Map<String, JsonNode> respostasDasAreas(Map<String, JsonNode> doProcesso) {
        Map<String, JsonNode> respostas = new HashMap<>();
        doProcesso.forEach((nome, valor) -> {
            if (!"areasAfetadas".equals(nome) && valor.isArray()) {
                valor.forEach(item -> {
                    if (item.hasNonNull("area") && item.has("aprovado")) {
                        respostas.put(item.path("area").asText(), item);
                    }
                });
            }
        });
        return respostas;
    }

    private String documentoDoPullRequest(Map<String, JsonNode> variaveis) {
        String caminho = textoDe(variaveis, "prCorpo");
        if (caminho == null || caminho.isBlank()) {
            return null;
        }
        try {
            Path arquivo = Path.of(caminho).toAbsolutePath().normalize();
            Path raiz = properties.workspaces().toAbsolutePath().normalize();
            if (!arquivo.startsWith(raiz) || !arquivo.getFileName().toString().endsWith(".md") || !Files.isRegularFile(arquivo)) {
                return null;
            }
            return Files.readString(arquivo);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private List<JsonNode> completas(List<JsonNode> variaveis) {
        return variaveis.stream()
                .map(v -> v.path("isTruncated").asBoolean(false)
                        ? camunda.obter("variables/{chave}", v.path("variableKey").asText())
                        : v)
                .toList();
    }

    private Map<String, Map<String, JsonNode>> variaveisDoProcesso(List<JsonNode> variaveis) {
        Map<String, Map<String, JsonNode>> porInstancia = new HashMap<>();
        for (JsonNode variavel : variaveis) {
            String instancia = variavel.path("processInstanceKey").asText();
            if (instancia.equals(variavel.path("scopeKey").asText())) {
                porInstancia.computeIfAbsent(instancia, i -> new HashMap<>())
                        .put(variavel.path("name").asText(), valor(variavel));
            }
        }
        return porInstancia;
    }

    private static Map<String, List<JsonNode>> porInstancia(List<JsonNode> elementos) {
        Map<String, List<JsonNode>> agrupados = new HashMap<>();
        elementos.forEach(e -> agrupados.computeIfAbsent(e.path("processInstanceKey").asText(), k -> new ArrayList<>()).add(e));
        return agrupados;
    }

    JsonNode valor(JsonNode variavel) {
        String bruto = variavel.path("value").asText(null);
        if (bruto == null) {
            return NullNode.getInstance();
        }
        try {
            JsonNode lido = json.readTree(bruto);
            return lido == null || lido.isMissingNode() ? NullNode.getInstance() : lido;
        } catch (JsonProcessingException e) {
            return TextNode.valueOf(bruto.startsWith("\"") ? bruto.substring(1) : bruto);
        }
    }

    private static String texto(JsonNode no, String campo) {
        return no.hasNonNull(campo) ? no.path(campo).asText() : null;
    }

    private static String textoDe(Map<String, JsonNode> variaveis, String nome) {
        JsonNode valor = variaveis.get(nome);
        return valor == null || valor.isNull() ? null : valor.asText();
    }

    private static Double numeroDe(Map<String, JsonNode> variaveis, String nome) {
        JsonNode valor = variaveis.get(nome);
        return valor != null && valor.isNumber() ? valor.asDouble() : null;
    }

    private static Integer inteiroDe(Map<String, JsonNode> variaveis, String nome) {
        JsonNode valor = variaveis.get(nome);
        return valor != null && valor.isNumber() ? valor.asInt() : null;
    }
}
