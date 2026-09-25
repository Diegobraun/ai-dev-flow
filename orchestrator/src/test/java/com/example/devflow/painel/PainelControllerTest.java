package com.example.devflow.painel;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.devflow.DevFlowProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClient;

class PainelControllerTest {

    private static final String CAMUNDA = "http://camunda";

    @TempDir
    Path workspaces;

    private final ObjectMapper json = new ObjectMapper();
    private MockRestServiceServer camunda;
    private MockMvc mvc;

    @BeforeEach
    void montar() {
        RestClient.Builder builder = RestClient.builder().baseUrl(CAMUNDA);
        camunda = MockRestServiceServer.bindTo(builder).build();
        DevFlowProperties properties = new DevFlowProperties(workspaces, Path.of("plugin"), "claude", null,
                Duration.ofMinutes(1), BigDecimal.ONE, Map.of(), 3, false);
        Painel painel = new Painel(new CamundaRest(builder.build()), json, properties);
        mvc = MockMvcBuilders.standaloneSetup(new PainelController(painel, json)).build();
    }

    @Test
    void listaTarefasComEtapaCustoRodadaEPendencia() throws Exception {
        esperarBusca("process-instances", """
                {"items":[
                  {"processInstanceKey":"1","state":"ACTIVE","hasIncident":false,"startDate":"2026-09-25T10:00:00Z"},
                  {"processInstanceKey":"2","state":"COMPLETED","hasIncident":false,"startDate":"2026-09-24T10:00:00Z","endDate":"2026-09-24T11:00:00Z"},
                  {"processInstanceKey":"3","state":"ACTIVE","hasIncident":true,"startDate":"2026-09-23T10:00:00Z"}
                ]}""");
        esperarBusca("variables", """
                {"items":[
                  %s, %s, %s, %s, %s, %s
                ]}""".formatted(
                variavel("1", "1", "tarefa", "\"limite-pix\""),
                variavel("1", "1", "repositorio", "\"git@x/pagamentos.git\""),
                variavel("1", "1", "custoUsd", "0.69"),
                variavel("1", "1", "rodadaDeRefinamento", "2"),
                variavel("2", "2", "tarefa", "\"cancelada\""),
                variavel("3", "3", "tarefa", "\"com-falha\"")));
        esperarBusca("element-instances", """
                {"items":[
                  {"processInstanceKey":"1","elementId":"aprovar-refinamento","elementName":"Aprovar refinamento","type":"USER_TASK","state":"ACTIVE"},
                  {"processInstanceKey":"3","elementId":"desenvolver","elementName":"Desenvolver código","type":"SERVICE_TASK","state":"ACTIVE"}
                ]}""");
        esperarBusca("element-instances", """
                {"items":[{"processInstanceKey":"2","elementId":"tarefa-cancelada","type":"END_EVENT","state":"COMPLETED"}]}""");
        esperarBusca("user-tasks", """
                {"items":[{"userTaskKey":"77","elementId":"aprovar-refinamento","name":"Aprovar refinamento",
                  "processInstanceKey":"1","elementInstanceKey":"10","candidateGroups":[],"creationDate":"2026-09-25T10:05:00Z"}]}""");
        esperarBusca("variables", """
                {"items":[%s]}""".formatted(variavel("1", "1", "tarefa", "\"limite-pix\"")));

        mvc.perform(get("/api/tarefas"))
                .andExpect(status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$", hasSize(3)))
                .andExpect(resposta("$[0].tarefa", "limite-pix"))
                .andExpect(resposta("$[0].etapa", "aprovacao"))
                .andExpect(resposta("$[0].estado", "ativa"))
                .andExpect(resposta("$[0].custoUsd", 0.69))
                .andExpect(resposta("$[0].rodadaDeRefinamento", 2))
                .andExpect(resposta("$[0].pendencias[0].userTaskKey", "77"))
                .andExpect(resposta("$[0].pendencias[0].tarefa", "limite-pix"))
                .andExpect(resposta("$[1].estado", "cancelada"))
                .andExpect(resposta("$[1].etapa", "cancelada"))
                .andExpect(resposta("$[2].estado", "incidente"))
                .andExpect(resposta("$[2].etapa", "desenvolvimento"));
        camunda.verify();
    }

    @Test
    void detalheTrazHistoricoIncidentesAreasEDocumentoDoPullRequest() throws Exception {
        Path pr = Files.writeString(Files.createDirectories(workspaces.resolve("limite-pix/.devflow/limite-pix")).resolve("pr.md"),
                "## Corpo do PR");
        camunda.expect(requestTo(CAMUNDA + "/v2/process-instances/1"))
                .andRespond(withSuccess("""
                        {"processInstanceKey":"1","state":"ACTIVE","hasIncident":true,"startDate":"2026-09-25T10:00:00Z"}""",
                        MediaType.APPLICATION_JSON));
        esperarBusca("variables", """
                {"items":[%s, %s, %s, %s, %s, %s, %s, %s,
                  {"variableKey":"900","name":"refinamentoDocumento","value":"\\"# Refin","scopeKey":"1","processInstanceKey":"1","isTruncated":true}
                ]}""".formatted(
                variavel("1", "1", "tarefa", "\"limite-pix\""),
                variavel("1", "1", "prCorpo", json.writeValueAsString(pr.toString())),
                variavel("1", "1", "areasAfetadas", """
                        [{"area":"pagamentos","times":["pix"],"servicos":["payment-service"],"contratos":["POST /pix"]},
                         {"area":"contas","times":["core"],"servicos":["account-service"],"contratos":["GET /accounts"]},
                         {"area":"credito","times":["risco"],"servicos":["credit-service"],"contratos":[]}]"""),
                variavel("1", "20", "aprovacao", "{\"area\":\"pagamentos\"}"),
                variavel("1", "21", "aprovacao", "{\"area\":\"contas\"}"),
                variavel("1", "21", "aprovado", "false"),
                variavel("1", "21", "motivo", "\"quebra o contrato\""),
                variavel("1", "22", "aprovacao", "{\"area\":\"credito\"}")));
        camunda.expect(requestTo(CAMUNDA + "/v2/variables/900"))
                .andRespond(withSuccess("""
                        {"variableKey":"900","name":"refinamentoDocumento","value":"\\"# Refinamento completo\\"","scopeKey":"1","processInstanceKey":"1","isTruncated":false}""",
                        MediaType.APPLICATION_JSON));
        esperarBusca("element-instances", """
                {"items":[
                  {"elementInstanceKey":"12","processInstanceKey":"1","elementId":"refinar","elementName":"Refinamento técnico","type":"SERVICE_TASK","state":"COMPLETED","startDate":"2026-09-25T10:01:00Z","endDate":"2026-09-25T10:02:00Z"},
                  {"elementInstanceKey":"11","processInstanceKey":"1","elementId":"tarefa-recebida","type":"START_EVENT","state":"COMPLETED","startDate":"2026-09-25T10:00:00Z"},
                  {"elementInstanceKey":"19","processInstanceKey":"1","elementId":"aprovar-entre-areas","type":"MULTI_INSTANCE_BODY","state":"ACTIVE","startDate":"2026-09-25T10:03:00Z"},
                  {"elementInstanceKey":"20","processInstanceKey":"1","elementId":"aprovar-entre-areas","elementName":"Aprovar entre áreas","type":"USER_TASK","state":"ACTIVE","startDate":"2026-09-25T10:03:01Z","hasIncident":false},
                  {"elementInstanceKey":"21","processInstanceKey":"1","elementId":"aprovar-entre-areas","elementName":"Aprovar entre áreas","type":"USER_TASK","state":"COMPLETED","startDate":"2026-09-25T10:03:02Z"},
                  {"elementInstanceKey":"23","processInstanceKey":"1","elementId":"desenvolver","elementName":"Desenvolver código","type":"SERVICE_TASK","state":"ACTIVE","startDate":"2026-09-25T10:04:00Z","hasIncident":true}
                ]}""");
        esperarBusca("incidents", """
                {"items":[{"incidentKey":"5","elementId":"desenvolver","errorType":"JOB_NO_RETRIES","errorMessage":"orçamento estourado","creationTime":"2026-09-25T10:05:00Z"}]}""");
        esperarBusca("user-tasks", """
                {"items":[{"userTaskKey":"88","elementId":"aprovar-entre-areas","name":"Aprovar entre áreas",
                  "processInstanceKey":"1","elementInstanceKey":"20","candidateGroups":["pagamentos"]}]}""");

        mvc.perform(get("/api/tarefas/1"))
                .andExpect(status().isOk())
                .andExpect(resposta("$.resumo.estado", "incidente"))
                .andExpect(resposta("$.resumo.etapa", "aprovacao"))
                .andExpect(resposta("$.variaveis.refinamentoDocumento", "# Refinamento completo"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.historico[*].elementId",
                        contains("tarefa-recebida", "refinar", "aprovar-entre-areas", "aprovar-entre-areas", "desenvolver")))
                .andExpect(resposta("$.historico[0].etapa", "refinamento"))
                .andExpect(resposta("$.historico[4].incidente", true))
                .andExpect(resposta("$.incidentes[0].mensagem", "orçamento estourado"))
                .andExpect(resposta("$.pendencias[0].area", "pagamentos"))
                .andExpect(resposta("$.areas[0].area", "pagamentos"))
                .andExpect(resposta("$.areas[0].status", "pendente"))
                .andExpect(resposta("$.areas[0].userTaskKey", "88"))
                .andExpect(resposta("$.areas[0].servicos[0]", "payment-service"))
                .andExpect(resposta("$.areas[1].status", "recusada"))
                .andExpect(resposta("$.areas[1].motivo", "quebra o contrato"))
                .andExpect(resposta("$.areas[2].status", "aguardando"))
                .andExpect(resposta("$.prDocumento", "## Corpo do PR"));
        camunda.verify();
    }

    @Test
    void detalheSemAreasDevolveListaVazia() throws Exception {
        camunda.expect(requestTo(CAMUNDA + "/v2/process-instances/1"))
                .andRespond(withSuccess("""
                        {"processInstanceKey":"1","state":"COMPLETED","hasIncident":false}""", MediaType.APPLICATION_JSON));
        esperarBusca("variables", """
                {"items":[%s, %s]}""".formatted(
                variavel("1", "1", "tarefa", "\"limite-pix\""),
                variavel("1", "1", "prCorpo", "\"/etc/passwd\"")));
        esperarBusca("element-instances", """
                {"items":[{"elementInstanceKey":"30","processInstanceKey":"1","elementId":"tarefa-concluida","type":"END_EVENT","state":"COMPLETED"}]}""");
        esperarBusca("incidents", "{\"items\":[]}");
        esperarBusca("user-tasks", "{\"items\":[]}");

        mvc.perform(get("/api/tarefas/1"))
                .andExpect(status().isOk())
                .andExpect(resposta("$.resumo.estado", "concluida"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.areas", hasSize(0)))
                .andExpect(MockMvcResultMatchers.jsonPath("$.prDocumento").doesNotExist());
    }

    @Test
    void pendenciaJuntaVariaveisDoProcessoComAsLocais() throws Exception {
        camunda.expect(requestTo(CAMUNDA + "/v2/user-tasks/88"))
                .andRespond(withSuccess("""
                        {"userTaskKey":"88","elementId":"aprovar-entre-areas","name":"Aprovar entre áreas","state":"CREATED",
                         "processInstanceKey":"1","elementInstanceKey":"20","candidateGroups":["pagamentos"]}""",
                        MediaType.APPLICATION_JSON));
        esperarBusca("variables", """
                {"items":[%s, %s, %s]}""".formatted(
                variavel("1", "1", "tarefa", "\"limite-pix\""),
                variavel("1", "20", "aprovacao", "{\"area\":\"pagamentos\",\"times\":[\"pix\"]}"),
                variavel("1", "21", "aprovacao", "{\"area\":\"contas\"}")));

        mvc.perform(get("/api/pendencias/88"))
                .andExpect(status().isOk())
                .andExpect(resposta("$.tarefa", "limite-pix"))
                .andExpect(resposta("$.area", "pagamentos"))
                .andExpect(resposta("$.variaveis.aprovacao.times[0]", "pix"))
                .andExpect(resposta("$.candidateGroups[0]", "pagamentos"));
    }

    @Test
    void filtraPendenciasPorGrupo() throws Exception {
        camunda.expect(requestTo(CAMUNDA + "/v2/user-tasks/search"))
                .andExpect(jsonPath("$.filter.candidateGroup").value("pagamentos"))
                .andExpect(jsonPath("$.filter.state").value("CREATED"))
                .andRespond(withSuccess("{\"items\":[]}", MediaType.APPLICATION_JSON));

        mvc.perform(get("/api/pendencias").param("grupo", "pagamentos"))
                .andExpect(status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$", hasSize(0)));
        camunda.verify();
    }

    @Test
    void concluiPendenciaRepassandoAsVariaveis() throws Exception {
        esperarTarefa("77", "aprovar-refinamento");
        camunda.expect(requestTo(CAMUNDA + "/v2/user-tasks/77/completion"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"variables":{"refinamentoAprovado":false,"observacoesDoRefinamento":"limite por faixa"}}"""))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        mvc.perform(post("/api/pendencias/77/conclusao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refinamentoAprovado\":false,\"observacoesDoRefinamento\":\"limite por faixa\"}"))
                .andExpect(status().isNoContent());
        camunda.verify();
    }

    @Test
    void recusaEntreAreasSemMotivoNaoChegaAoCamunda() throws Exception {
        esperarTarefa("88", "aprovar-entre-areas");

        mvc.perform(post("/api/pendencias/88/conclusao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aprovado\":false,\"motivo\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(resposta("$.erro", "O motivo é obrigatório para recusar"));
        camunda.verify();
    }

    @Test
    void falhaDoCamundaViraBadGatewayComODetalhe() throws Exception {
        camunda.expect(requestTo(CAMUNDA + "/v2/process-instances/search"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("{\"title\":\"UNAVAILABLE\",\"detail\":\"The search client could not connect to the search server\"}"));

        mvc.perform(get("/api/tarefas"))
                .andExpect(status().isBadGateway())
                .andExpect(resposta("$.erro", "The search client could not connect to the search server"));
    }

    private void esperarBusca(String recurso, String corpo) {
        camunda.expect(requestTo(CAMUNDA + "/v2/" + recurso + "/search"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(corpo, MediaType.APPLICATION_JSON));
    }

    private void esperarTarefa(String chave, String elementId) {
        camunda.expect(requestTo(CAMUNDA + "/v2/user-tasks/" + chave))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"userTaskKey":"%s","elementId":"%s","state":"CREATED","processInstanceKey":"1"}""".formatted(chave, elementId),
                        MediaType.APPLICATION_JSON));
    }

    private String variavel(String instancia, String escopo, String nome, String valorJson) throws Exception {
        return json.writeValueAsString(Map.of(
                "variableKey", instancia + escopo + nome,
                "name", nome,
                "value", valorJson,
                "scopeKey", escopo,
                "processInstanceKey", instancia,
                "isTruncated", false));
    }

    private static ResultMatcher resposta(String caminho, Object esperado) {
        return MockMvcResultMatchers.jsonPath(caminho).value(esperado);
    }
}
