package com.example.devflow.painel;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;

public record DetalheDaTarefa(
        ResumoDaTarefa resumo,
        Map<String, JsonNode> variaveis,
        List<PassoDoHistorico> historico,
        List<Incidente> incidentes,
        List<Pendencia> pendencias,
        List<AreaAfetada> areas,
        String prDocumento) {
}
