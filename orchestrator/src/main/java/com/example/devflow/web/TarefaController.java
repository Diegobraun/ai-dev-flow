package com.example.devflow.web;

import com.example.devflow.DevFlowProperties;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import jakarta.validation.Valid;
import java.text.Normalizer;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/tarefas")
public class TarefaController {

    static final String PROCESSO = "dev-flow";

    private final CamundaClient camunda;
    private final DevFlowProperties properties;

    public TarefaController(CamundaClient camunda, DevFlowProperties properties) {
        this.camunda = camunda;
        this.properties = properties;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> iniciar(@RequestBody @Valid NovaTarefa pedido) {
        String tarefa = pedido.tarefa() != null ? pedido.tarefa() : slug(pedido.descricao());
        String branchBase = pedido.branchBase() == null || pedido.branchBase().isBlank() ? "main" : pedido.branchBase();
        ProcessInstanceEvent instancia = camunda.newCreateInstanceCommand()
                .bpmnProcessId(PROCESSO)
                .latestVersion()
                .variables(Map.of(
                        "tarefa", tarefa,
                        "descricao", pedido.descricao(),
                        "repositorio", pedido.repositorio(),
                        "branchBase", branchBase,
                        "limiteDeRevisoes", pedido.limiteDeRevisoes() != null
                                ? pedido.limiteDeRevisoes()
                                : properties.limiteDeRevisoes()))
                .send()
                .join();
        return Map.of("tarefa", tarefa, "processInstanceKey", String.valueOf(instancia.getProcessInstanceKey()));
    }

    static String slug(String descricao) {
        String semAcento = Normalizer.normalize(descricao, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String base = semAcento.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (base.length() > 40) {
            base = base.substring(0, 40).replaceAll("-[^-]*$", "");
        }
        return base + "-" + Integer.toString(ThreadLocalRandom.current().nextInt(1296, 46656), 36);
    }
}
