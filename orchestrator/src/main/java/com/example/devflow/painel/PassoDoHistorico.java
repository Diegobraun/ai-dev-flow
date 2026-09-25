package com.example.devflow.painel;

public record PassoDoHistorico(
        String elementInstanceKey,
        String elementId,
        String nome,
        String tipo,
        String etapa,
        String estado,
        String inicio,
        String fim,
        boolean incidente) {
}
