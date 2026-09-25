package com.example.devflow.painel;

import java.util.List;

public record ResumoDaTarefa(
        String processInstanceKey,
        String tarefa,
        String descricao,
        String repositorio,
        String branch,
        String estado,
        String etapa,
        List<String> elementosAtivos,
        Double custoUsd,
        Integer rodadaDeRefinamento,
        Integer rodadaDeDesenvolvimento,
        Integer rodadaDeRevisao,
        Integer limiteDeRevisoes,
        String prUrl,
        boolean incidente,
        List<Pendencia> pendencias,
        String inicio,
        String fim) {
}
