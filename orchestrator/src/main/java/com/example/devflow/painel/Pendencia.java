package com.example.devflow.painel;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Pendencia(
        String userTaskKey,
        String elementId,
        String nome,
        String processInstanceKey,
        String elementInstanceKey,
        String tarefa,
        List<String> candidateGroups,
        String criadaEm,
        String area,
        Map<String, JsonNode> variaveis) {
}
