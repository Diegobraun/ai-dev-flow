package com.example.devflow.painel;

import java.util.Map;

final class Etapas {

    static final String OUTRA = "outra";

    private static final Map<String, String> POR_ELEMENTO = Map.ofEntries(
            Map.entry("tarefa-recebida", "refinamento"),
            Map.entry("preparar-workspace", "refinamento"),
            Map.entry("refinar", "refinamento"),
            Map.entry("aprovar-refinamento", "aprovacao"),
            Map.entry("refinamento-aprovado", "aprovacao"),
            Map.entry("aprovar-entre-areas", "aprovacao"),
            Map.entry("outras-areas", "aprovacao"),
            Map.entry("areas-aprovaram", "aprovacao"),
            Map.entry("desenvolver", "desenvolvimento"),
            Map.entry("implementou", "desenvolvimento"),
            Map.entry("revisar", "review"),
            Map.entry("veredito", "review"),
            Map.entry("decidir-review", "review"),
            Map.entry("decisao-review", "review"),
            Map.entry("testar", "teste"),
            Map.entry("testes-aprovados", "teste"),
            Map.entry("decidir-testes", "teste"),
            Map.entry("decisao-testes", "teste"),
            Map.entry("aprovar-pr", "pr"),
            Map.entry("pr-aprovado", "pr"),
            Map.entry("abrir-pr", "pr"),
            Map.entry("tarefa-concluida", "concluida"),
            Map.entry("tarefa-cancelada", "cancelada"));

    private Etapas() {
    }

    static String de(String elementId) {
        if (elementId == null) {
            return OUTRA;
        }
        String etapa = POR_ELEMENTO.get(elementId);
        if (etapa != null) {
            return etapa;
        }
        return elementId.contains("area") ? "aprovacao" : OUTRA;
    }

    static boolean conhecida(String elementId) {
        return !OUTRA.equals(de(elementId));
    }
}
