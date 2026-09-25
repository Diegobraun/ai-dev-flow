package com.example.devflow.fluxo;

import static io.camunda.process.test.api.CamundaAssert.assertThat;
import static io.camunda.process.test.api.assertions.UserTaskSelectors.byElementId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.devflow.agente.Agente;
import com.example.devflow.agente.AgenteFalhou;
import com.example.devflow.agente.ChamadaDoAgente;
import com.example.devflow.agente.Etapa;
import com.example.devflow.agente.ResultadoDoAgente;
import com.example.devflow.workspace.Workspace;
import com.example.devflow.workspace.Workspaces;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@CamundaSpringProcessTest
class DevFlowProcessTest {

    private static final Path WORKSPACE = Path.of("/tmp/devflow-teste");

    @Autowired
    private CamundaClient client;

    @Autowired
    private CamundaProcessTestContext contexto;

    @MockitoBean
    private Agente agente;

    @MockitoBean
    private Workspaces workspaces;

    private final ObjectMapper json = new ObjectMapper();
    private final Map<Etapa, Deque<String>> respostas = new EnumMap<>(Etapa.class);
    private final List<ChamadaDoAgente> chamadas = new ArrayList<>();

    @BeforeEach
    void simularWorkspaceEAgente() {
        when(workspaces.preparar(anyString(), anyString(), anyString()))
                .thenReturn(new Workspace(WORKSPACE, "devflow/limite-pix", "base123"));
        when(workspaces.head(any())).thenReturn("head456");
        when(workspaces.documento(any(), anyString(), anyString())).thenReturn("---\netapa: x\n---\nconteúdo");
        when(workspaces.log(any(), anyString(), anyString())).thenAnswer(i -> WORKSPACE.resolve("logs").resolve(i.getArgument(2, String.class)));
        when(workspaces.escrever(any(), anyString(), anyString(), anyString())).thenReturn(WORKSPACE.resolve("pr.md"));
        when(workspaces.commitsDesde(any(), anyString())).thenReturn(3);
        when(agente.executar(any())).thenAnswer(invocacao -> {
            ChamadaDoAgente chamada = invocacao.getArgument(0);
            chamadas.add(chamada);
            String resposta = respostas.get(chamada.etapa()).size() > 1
                    ? respostas.get(chamada.etapa()).poll()
                    : respostas.get(chamada.etapa()).peek();
            return new ResultadoDoAgente(json.readTree(resposta), 0.25, 10, Duration.ofMinutes(1));
        });
        responder(Etapa.REFINAMENTO, "{\"status\":\"pronto\",\"resumo\":\"limite diário\",\"perguntas\":[]}");
        responder(Etapa.DESENVOLVIMENTO, "{\"build\":\"ok\",\"resumo\":\"2 arquivos\",\"commits\":1}");
        responder(Etapa.REVISAO, "{\"veredito\":\"aprovado\",\"bloqueantes\":0,\"importantes\":1,\"resumo\":\"ok\"}");
        responder(Etapa.TESTE, "{\"resultado\":\"aprovado\",\"criterios\":4,\"falhas\":0,\"resumo\":\"4 critérios\"}");
    }

    @Test
    void caminhoFelizComDuasAprovacoesHumanas() {
        ProcessInstanceEvent instancia = iniciar();

        assertThat(instancia).isActive().hasActiveElements("aprovar-refinamento");
        contexto.completeUserTask("aprovar-refinamento", Map.of("refinamentoAprovado", true));

        assertThat(instancia).hasActiveElements("aprovar-pr")
                .hasCompletedElements("desenvolver", "revisar", "testar")
                .hasVariable("veredito", "aprovado")
                .hasVariable("commitAtual", "head456")
                .hasVariable("custoUsd", 1.0);
        contexto.completeUserTask("aprovar-pr", Map.of("prAprovado", true));

        assertThat(instancia).isCompleted()
                .hasCompletedElements("abrir-pr", "tarefa-concluida")
                .hasVariable("commits", 3)
                .hasVariable("prUrl", "");
        verify(workspaces, never()).abrirPullRequest(any(), anyString(), anyString(), any());
    }

    @Test
    void refinamentoReprovadoVoltaComAsObservacoes() {
        ProcessInstanceEvent instancia = iniciar();

        assertThat(instancia).hasActiveElements("aprovar-refinamento");
        contexto.completeUserTask("aprovar-refinamento",
                Map.of("refinamentoAprovado", false, "observacoesDoRefinamento", "o limite é R$ 5.000"));

        assertThat(instancia).hasActiveElements("aprovar-refinamento").hasVariable("rodadaDeRefinamento", 2);
        assertThat(chamadas).hasSize(2);
        assertThat(chamadas.get(1).prompt()).contains("o limite é R$ 5.000");
        assertThat(chamadas.get(0).prompt()).doesNotContain("Observações");
    }

    @Test
    void mudancaQueAfetaOutrasAreasEsperaAAprovacaoDeCadaUma() {
        responder(Etapa.REFINAMENTO, refinamentoAfetando("credito", "pagamentos"));
        ProcessInstanceEvent instancia = iniciar();
        contexto.completeUserTask("aprovar-refinamento", Map.of("refinamentoAprovado", true));

        assertThat(instancia).hasActiveElement("aprovar-entre-areas", 3).hasNotActivatedElements("desenvolver");
        contexto.completeUserTask(byElementId("aprovar-entre-areas"), Map.of("aprovado", true));
        assertThat(instancia).hasActiveElement("aprovar-entre-areas", 2);
        contexto.completeUserTask(byElementId("aprovar-entre-areas"), Map.of("aprovado", true));

        assertThat(instancia).hasActiveElements("aprovar-pr").hasCompletedElement("aprovar-entre-areas", 3);
        assertThat(chamadas.stream().filter(c -> c.etapa() == Etapa.REVISAO).findFirst().orElseThrow().prompt())
                .contains("Áreas que aprovaram a mudança: credito, pagamentos");
    }

    @Test
    void areaQueRecusaDevolveORefinamentoComOMotivo() {
        responder(Etapa.REFINAMENTO, refinamentoAfetando("credito"));
        ProcessInstanceEvent instancia = iniciar();
        contexto.completeUserTask("aprovar-refinamento", Map.of("refinamentoAprovado", true));

        contexto.completeUserTask(byElementId("aprovar-entre-areas"),
                Map.of("aprovado", false, "motivo", "o loan-service ainda não trata o campo novo"));

        assertThat(instancia).hasActiveElements("aprovar-refinamento")
                .hasCompletedElement("refinar", 2)
                .hasNotActivatedElements("desenvolver")
                .hasVariable("aprovacoesEntreAreas", null);
        assertThat(chamadas.get(1).prompt()).contains("- credito: o loan-service ainda não trata o campo novo");
    }

    @Test
    void semOutrasAreasVaiDiretoParaODesenvolvimento() {
        ProcessInstanceEvent instancia = iniciar();
        contexto.completeUserTask("aprovar-refinamento", Map.of("refinamentoAprovado", true));

        assertThat(instancia).hasActiveElements("aprovar-pr").hasNotActivatedElements("aprovar-entre-areas");
        assertThat(chamadas.stream().filter(c -> c.etapa() == Etapa.REVISAO).findFirst().orElseThrow().prompt())
                .contains("Áreas que aprovaram a mudança: nenhuma");
    }

    @Test
    void reviewBloqueadoVoltaParaODesenvolvimentoAteOLimite() {
        responder(Etapa.REVISAO, "{\"veredito\":\"bloqueado\",\"bloqueantes\":1,\"importantes\":0,\"resumo\":\"B1 saldo\"}");
        ProcessInstanceEvent instancia = iniciar();
        contexto.completeUserTask("aprovar-refinamento", Map.of("refinamentoAprovado", true));

        assertThat(instancia).hasActiveElements("decidir-review")
                .hasCompletedElement("desenvolver", 3)
                .hasCompletedElement("revisar", 3)
                .hasVariable("rodadaDeRevisao", 3)
                .hasVariable("correcao", "review-3.md");
        List<String> prompts = chamadas.stream().filter(c -> c.etapa() == Etapa.DESENVOLVIMENTO).map(ChamadaDoAgente::prompt).toList();
        assertThat(prompts.get(0)).contains("Implemente o refinamento");
        assertThat(prompts.get(1)).contains("review-1.md");
        assertThat(prompts.get(2)).contains("review-2.md");
        assertThat(chamadas.stream().filter(c -> c.etapa() == Etapa.REVISAO).map(ChamadaDoAgente::prompt))
                .allMatch(p -> p.contains("base123...HEAD"));

        contexto.completeUserTask("decidir-review", Map.of("decisaoDoReview", "cancelar"));

        assertThat(instancia).isCompleted().hasCompletedElements("tarefa-cancelada");
    }

    @Test
    void reviewCorrigidoNaSegundaRodadaSegueParaOsTestes() {
        responder(Etapa.REVISAO,
                "{\"veredito\":\"bloqueado\",\"bloqueantes\":2,\"importantes\":0,\"resumo\":\"B1 B2\"}",
                "{\"veredito\":\"aprovado\",\"bloqueantes\":0,\"importantes\":0,\"resumo\":\"ok\"}");
        ProcessInstanceEvent instancia = iniciar();
        contexto.completeUserTask("aprovar-refinamento", Map.of("refinamentoAprovado", true));

        assertThat(instancia).hasActiveElements("aprovar-pr")
                .hasCompletedElement("desenvolver", 2)
                .hasCompletedElement("revisar", 2)
                .hasVariable("rodadaDeRevisao", 2);
        verify(workspaces, atLeastOnce()).restaurar(WORKSPACE, "head456");
    }

    @Test
    void testesQueFalhamVoltamParaODesenvolvimentoPelaDecisaoHumana() {
        responder(Etapa.TESTE,
                "{\"resultado\":\"falhou\",\"criterios\":4,\"falhas\":1,\"resumo\":\"critério 3\"}",
                "{\"resultado\":\"aprovado\",\"criterios\":4,\"falhas\":0,\"resumo\":\"ok\"}");
        ProcessInstanceEvent instancia = iniciar();
        contexto.completeUserTask("aprovar-refinamento", Map.of("refinamentoAprovado", true));

        assertThat(instancia).hasActiveElements("decidir-testes").hasVariable("correcao", "testes.md");
        contexto.completeUserTask("decidir-testes", Map.of("decisaoDosTestes", "corrigir"));

        assertThat(instancia).hasActiveElements("aprovar-pr").hasCompletedElement("desenvolver", 2);
        assertThat(chamadas.stream().filter(c -> c.etapa() == Etapa.DESENVOLVIMENTO).toList().get(1).prompt())
                .contains("testes.md");
    }

    @Test
    void falhaDoAgenteViraIncidente() {
        doThrow(new AgenteFalhou("refinador passou de 45m")).when(agente).executar(any());

        ProcessInstanceEvent instancia = iniciar();

        assertThat(instancia).isActive().hasActiveIncidents();
    }

    @Test
    void inicioPeloFormularioAssumeBranchMainETresRodadas() {
        ProcessInstanceEvent instancia = client.newCreateInstanceCommand()
                .bpmnProcessId("dev-flow")
                .latestVersion()
                .variables(Map.of(
                        "tarefa", "validar-cpf",
                        "descricao", "Validar CPF no cadastro",
                        "repositorio", "/tmp/customer-service"))
                .send()
                .join();

        assertThat(instancia).hasActiveElements("aprovar-refinamento")
                .hasVariable("branchBase", "main")
                .hasVariable("limiteDeRevisoes", 3);
        verify(workspaces).preparar("validar-cpf", "/tmp/customer-service", "main");
    }

    private static String refinamentoAfetando(String... areas) {
        String lista = String.join(",", Arrays.stream(areas).map(a ->
                "{\"area\":\"" + a + "\",\"times\":[\"Time " + a + "\"],\"servicos\":[\"x-service\"],\"contratos\":[\"account-opened\"]}").toList());
        return "{\"status\":\"pronto\",\"resumo\":\"campo novo\",\"perguntas\":[],\"areasAfetadas\":[" + lista + "]}";
    }

    private void responder(Etapa etapa, String... jsons) {
        respostas.put(etapa, new ArrayDeque<>(List.of(jsons)));
    }

    private ProcessInstanceEvent iniciar() {
        return client.newCreateInstanceCommand()
                .bpmnProcessId("dev-flow")
                .latestVersion()
                .variables(Map.of(
                        "tarefa", "limite-pix",
                        "descricao", "Limite diário de PIX",
                        "repositorio", "https://github.com/exemplo/payment-service.git",
                        "branchBase", "main",
                        "limiteDeRevisoes", 3))
                .send()
                .join();
    }
}
